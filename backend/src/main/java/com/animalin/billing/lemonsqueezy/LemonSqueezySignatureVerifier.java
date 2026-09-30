package com.animalin.billing.lemonsqueezy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class LemonSqueezySignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(LemonSqueezySignatureVerifier.class);
    private static final String HMAC_ALG = "HmacSHA256";

    public void verify(String rawBody, String signatureHeader, String secret) {
        if (!StringUtils.hasText(secret)) {
            log.error("Lemon Squeezy webhook secret is not configured");
            throw new IllegalStateException("Lemon Squeezy webhook secret is not configured");
        }
        if (!StringUtils.hasText(signatureHeader) || rawBody == null || rawBody.isEmpty()) {
            log.warn("Rejected Lemon Squeezy webhook with a missing signature or body");
            throw new LemonSqueezySignatureException("Invalid webhook signature");
        }

        String provided = signatureHeader.trim();
        if (provided.regionMatches(true, 0, "sha256=", 0, "sha256=".length())) {
            provided = provided.substring("sha256=".length()).trim();
        }
        String expected = hmacHex(secret, rawBody);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.toLowerCase().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            log.warn("Rejected Lemon Squeezy webhook with an invalid signature");
            throw new LemonSqueezySignatureException("Invalid webhook signature");
        }
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            log.error("Unable to compute Lemon Squeezy webhook signature");
            throw new IllegalStateException("Unable to compute webhook signature");
        }
    }
}
