package com.animalin.notification;

import com.animalin.config.AnimalinProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class FirebaseAdminConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminConfig.class);

    private final AnimalinProperties properties;
    private volatile boolean ready;

    public FirebaseAdminConfig(AnimalinProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        AnimalinProperties.Fcm fcm = properties.fcm();
        if (fcm == null || !fcm.enabled()) {
            log.info("FCM_ENABLED=false. Push delivery is disabled and Firebase Admin will not start.");
            return;
        }
        String projectId = fcm.projectId() == null ? "" : fcm.projectId().trim();
        String encoded = fcm.serviceAccountBase64() == null ? "" : fcm.serviceAccountBase64().replaceAll("\\s", "");
        if (projectId.isEmpty() || encoded.isEmpty()) {
            log.error("FCM_ENABLED=true but Firebase credentials are incomplete. Set FIREBASE_PROJECT_ID and FIREBASE_SERVICE_ACCOUNT_BASE64. Push delivery is disabled.");
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            String json = new String(decoded, StandardCharsets.UTF_8);
            if (!json.contains("\"type\"") || !json.contains("service_account")) {
                log.error("FIREBASE_SERVICE_ACCOUNT_BASE64 does not contain a Firebase service account. Push delivery is disabled.");
                return;
            }
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            ready = true;
            log.info("Firebase Admin initialized for project {}", projectId);
        } catch (IllegalArgumentException ex) {
            log.error("FIREBASE_SERVICE_ACCOUNT_BASE64 is not valid Base64. Push delivery is disabled.");
        } catch (Exception ex) {
            log.error("Firebase Admin initialization failed ({}). Push delivery is disabled.", ex.getClass().getSimpleName());
        }
    }

    public boolean isReady() {
        return ready;
    }
}
