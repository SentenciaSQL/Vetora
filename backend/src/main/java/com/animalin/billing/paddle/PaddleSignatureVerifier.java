package com.animalin.billing.paddle;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class PaddleSignatureVerifier {

    private static final String HMAC_ALG = "HmacSHA256";

    public void verify(String signatureHeader, String rawBody, String secret, Duration tolerance, Instant now) {
        if (!StringUtils.hasText(secret)) {
            throw new PaddleSignatureException("Paddle webhook secret is not configured");
        }
        if (!StringUtils.hasText(signatureHeader)) {
            throw new PaddleSignatureException("Missing Paddle-Signature header");
        }
        if (rawBody == null) {
            throw new PaddleSignatureException("Missing webhook body");
        }

        ParsedSignature parsed = parse(signatureHeader);
        Instant timestamp = Instant.ofEpochSecond(parsed.ts());
        long skewSeconds = Math.abs(Duration.between(timestamp, now).getSeconds());
        if (skewSeconds > tolerance.getSeconds()) {
            throw new PaddleSignatureException("Expired or skewed Paddle webhook signature");
        }

        byte[] expected = hmac(secret, parsed.ts() + ":" + rawBody);
        boolean match = false;
        for (String candidate : parsed.h1()) {
            byte[] provided = decodeHex(candidate);
            if (provided.length == expected.length && MessageDigest.isEqual(expected, provided)) {
                match = true;
                break;
            }
        }
        if (!match) {
            throw new PaddleSignatureException("Invalid Paddle webhook signature");
        }
    }

    private ParsedSignature parse(String header) {
        Long ts = null;
        List<String> hashes = new ArrayList<>();
        for (String part : header.split(";")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim();
            String value = pair[1].trim();
            if ("ts".equals(key)) {
                try {
                    ts = Long.parseLong(value);
                } catch (NumberFormatException ex) {
                    throw new PaddleSignatureException("Invalid Paddle signature timestamp");
                }
            } else if ("h1".equals(key) && StringUtils.hasText(value)) {
                hashes.add(value.toLowerCase(Locale.ROOT));
            }
        }
        if (ts == null || hashes.isEmpty()) {
            throw new PaddleSignatureException("Malformed Paddle-Signature header");
        }
        return new ParsedSignature(ts, hashes);
    }

    private byte[] hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new PaddleSignatureException("Unable to compute webhook signature");
        }
    }

    private byte[] decodeHex(String hex) {
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException ex) {
            throw new PaddleSignatureException("Invalid Paddle signature digest");
        }
    }

    private record ParsedSignature(long ts, List<String> h1) {
    }
}
