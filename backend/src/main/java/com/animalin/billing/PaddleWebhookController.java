package com.animalin.billing;

import com.animalin.billing.paddle.PaddleSignatureException;
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
@RequestMapping("/api/v1/billing/webhooks")
public class PaddleWebhookController {

    private final PaddleWebhookService webhookService;

    public PaddleWebhookController(PaddleWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(path = "/paddle")
    public Map<String, Boolean> receive(
            @RequestHeader(value = "Paddle-Signature", required = false) String signature,
            HttpServletRequest request
    ) throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        webhookService.ingest(signature, rawBody);
        return Map.of("received", true);
    }

    @ExceptionHandler(PaddleSignatureException.class)
    public ResponseEntity<Map<String, String>> invalidSignature(PaddleSignatureException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid webhook signature"));
    }
}
