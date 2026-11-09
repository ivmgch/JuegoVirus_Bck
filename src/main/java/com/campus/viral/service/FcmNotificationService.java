package com.campus.viral.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FcmNotificationService {

    private static final Logger log = LoggerFactory.getLogger(FcmNotificationService.class);

    /**
     * Envía una notificación push multicast de alta prioridad a los jugadores sanos
     * al alcanzar el punto de inflexión poblacional (k / 2).
     */
    public void sendCriticalInflectionAlert(List<String> tokens, double k, long infectedCount) {
        if (tokens == null || tokens.isEmpty()) {
            log.info("No hay tokens FCM registrados para usuarios sanos en esta sesión.");
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[SIMULACIÓN FCM] Alerta de inflexión k/2 disparada para {} dispositivos. (k={}, infectados={})",
                tokens.size(), k, infectedCount);
            return;
        }

        try {
            MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(
                    Notification.builder()
                        .setTitle("⚠️ ¡ALERTA CRÍTICA: PICO DE PROPAGACIÓN!")
                        .setBody("El virus superó el punto de inflexión poblacional (k/2). La tasa de contagio alcanzó su velocidad máxima.")
                        .build()
                )
                .putData("type", "CRITICAL_INFLECTION")
                .putData("current_k", String.valueOf(k))
                .putData("infected_count", String.valueOf(infectedCount))
                .putData("priority", "HIGH")
                .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("FCM Multicast despachado con éxito: {} enviados, {} fallidos.",
                response.getSuccessCount(), response.getFailureCount());
        } catch (Exception e) {
            log.error("Fallo al despachar notificación push multicast vía Firebase: {}", e.getMessage(), e);
        }
    }
}
