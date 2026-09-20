package com.animalin.billing.paddle;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class PaddleSignatureVerifierTest {

    private static final String SECRET = "pdl_ntfset_test_secret";
    private static final String BODY = "{\"event_id\":\"evt_01h1\",\"event_type\":\"subscription.created\",\"data\":{}}";
    private final PaddleSignatureVerifier verifier = new PaddleSignatureVerifier();

    @Test
    void acceptsValidTsAndH1Signature() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String header = header(SECRET, now.getEpochSecond(), BODY);
        assertDoesNotThrow(() -> verifier.verify(header, BODY, SECRET, Duration.ofSeconds(5), now));
    }

    @Test
    void acceptsAnyMatchingH1DuringKeyRotation() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String valid = hmac(SECRET, now.getEpochSecond() + ":" + BODY);
        String header = "ts=" + now.getEpochSecond() + ";h1=deadbeef;h1=" + valid;
        assertDoesNotThrow(() -> verifier.verify(header, BODY, SECRET, Duration.ofSeconds(5), now));
    }

    @Test
    void rejectsMissingSignature() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> verifier.verify(null, BODY, SECRET, Duration.ofSeconds(5), now))
                .isInstanceOf(PaddleSignatureException.class)
                .hasMessageContaining("Missing");
    }

    @Test
    void rejectsInvalidSignature() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String header = "ts=" + now.getEpochSecond() + ";h1=" + "00".repeat(32);
        assertThatThrownBy(() -> verifier.verify(header, BODY, SECRET, Duration.ofSeconds(5), now))
                .isInstanceOf(PaddleSignatureException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void rejectsExpiredSignature() {
        Instant now = Instant.ofEpochSecond(1_700_000_100L);
        String header = header(SECRET, 1_700_000_000L, BODY);
        assertThatThrownBy(() -> verifier.verify(header, BODY, SECRET, Duration.ofSeconds(5), now))
                .isInstanceOf(PaddleSignatureException.class)
                .hasMessageContaining("Expired");
    }

    @Test
    void rejectsMalformedHeader() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> verifier.verify("not-a-signature", BODY, SECRET, Duration.ofSeconds(5), now))
                .isInstanceOf(PaddleSignatureException.class);
    }

    public static String header(String secret, long ts, String body) {
        return "ts=" + ts + ";h1=" + hmac(secret, ts + ":" + body);
    }

    static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
