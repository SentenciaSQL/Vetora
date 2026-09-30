package com.animalin.billing.lemonsqueezy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class LemonSqueezyWebhookController {

    private final LemonSqueezyWebhookService webhookService;

    public LemonSqueezyWebhookController(LemonSqueezyWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/lemonsqueezy")
    public Map<String, Boolean> receive(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Event-Name", required = false) String eventName,
            HttpServletRequest request
    ) throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        webhookService.ingest(signature, eventName, rawBody);
        return Map.of("received", true);
    }

    @ExceptionHandler(LemonSqueezySignatureException.class)
    public ResponseEntity<Map<String, String>> invalidSignature(LemonSqueezySignatureException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid webhook signature"));
    }

    @ExceptionHandler(LemonSqueezyPayloadException.class)
    public ResponseEntity<Map<String, String>> invalidPayload(LemonSqueezyPayloadException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid webhook payload"));
    }
}
