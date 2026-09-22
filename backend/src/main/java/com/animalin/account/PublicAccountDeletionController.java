package com.animalin.account;

import com.animalin.common.exception.ApiException;
import com.animalin.email.AppFrontendProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/v1/public/account-deletion-requests")
public class PublicAccountDeletionController {

    private final PublicAccountDeletionService service;
    private final AppFrontendProperties frontend;

    public PublicAccountDeletionController(PublicAccountDeletionService service, AppFrontendProperties frontend) {
        this.service = service;
        this.frontend = frontend;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Ack create(@Valid @RequestBody CreateRequest request) {
        return new Ack(service.requestDeletion(request.email(), request.reason(), request.confirmation()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> createForm(@RequestParam(required = false) String email,
                                              @RequestParam(required = false) String reason,
                                              @RequestParam(required = false) String confirmation) {
        boolean accepted = "true".equalsIgnoreCase(confirmation) || "on".equalsIgnoreCase(confirmation);
        try {
            String message = service.requestDeletion(email, reason, accepted);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page(message));
        } catch (ApiException ex) {
            return ResponseEntity.status(ex.getStatus()).contentType(MediaType.TEXT_HTML).body(page(ex.getMessage()));
        }
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConfirmResponse> confirm(@Valid @RequestBody ConfirmRequest request) {
        PublicAccountDeletionService.ConfirmResult result = service.confirm(request.token());
        return ResponseEntity.status(result.httpStatus())
                .cacheControl(CacheControl.noStore())
                .body(new ConfirmResponse(result.code(), result.message()));
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmForm(@RequestParam(required = false) String token) {
        PublicAccountDeletionService.ConfirmResult result = service.confirm(token);
        return ResponseEntity.status(result.httpStatus())
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(page(result.message()));
    }

    private String page(String message) {
        String safe = HtmlUtils.htmlEscape(message == null ? "" : message);
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="referrer" content="no-referrer">
                  <title>Eliminar cuenta y datos | LunaVeta</title>
                </head>
                <body style="margin:0;font-family:Segoe UI,Helvetica,Arial,sans-serif;background:#f8fafc;color:#0f172a;">
                  <main style="max-width:40rem;margin:0 auto;padding:2rem 1.25rem;">
                    <p style="margin:0;font-weight:800;color:#115e59;">LunaVeta</p>
                    <h1 style="font-size:1.6rem;">Eliminación de cuenta y datos de LunaVeta</h1>
                    <p>%s</p>
                    <p><a href="%s">Volver a la página de eliminación</a></p>
                  </main>
                </body>
                </html>
                """.formatted(safe, HtmlUtils.htmlEscape(frontend.frontendBaseUrl() + "/eliminar-cuenta"));
    }

    public record CreateRequest(
            @NotBlank @Email @Size(max = 180) String email,
            @Size(max = 500) String reason,
            @NotNull Boolean confirmation
    ) {
    }

    public record ConfirmRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record Ack(String message) {
    }

    public record ConfirmResponse(String code, String message) {
    }
}
