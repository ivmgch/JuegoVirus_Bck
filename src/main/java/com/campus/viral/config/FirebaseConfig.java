package com.campus.viral.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${app.firebase.service-account-path:firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void initFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            Resource resource = new ClassPathResource(serviceAccountPath);
            if (!resource.exists()) {
                resource = new FileSystemResource(serviceAccountPath);
            }

            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();
                    FirebaseApp.initializeApp(options);
                    log.info("✅ Firebase SDK inicializado exitosamente desde: {}", serviceAccountPath);
                } catch (Exception e) {
                    log.warn("⚠️ Archivo [{}] presente pero no contiene credenciales PKCS#8 válidas de Firebase. Continuando en modo simulación.",
                        serviceAccountPath);
                }
            } else {
                log.info("ℹ️ Firebase no configurado (archivo [{}] no encontrado). Alertas push operando en modo Simulación/Consola.",
                    serviceAccountPath);
            }
        } catch (Exception e) {
            log.info("ℹ️ Firebase en modo simulación: {}", e.getMessage());
        }
    }
}
