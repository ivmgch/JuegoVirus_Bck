package com.campus.viral.controller.ws;

import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.domain.SessionStatus;
import com.campus.viral.model.dto.request.ExposureReportPayload;
import com.campus.viral.model.dto.request.HeartbeatPayload;
import com.campus.viral.model.dto.response.BoardUpdateMessage;
import com.campus.viral.model.dto.response.StateChangeMessage;
import com.campus.viral.repository.redis.ActivePlayerRedisRepository;
import com.campus.viral.service.InfectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;

@Controller
public class GameWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketController.class);

    private final ActivePlayerRedisRepository redisRepository;
    private final InfectionService infectionService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(
        ActivePlayerRedisRepository redisRepository,
        InfectionService infectionService,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.redisRepository = redisRepository;
        this.infectionService = infectionService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Recepción de Heartbeat periódico desde los clientes móviles (cada 30s).
     * Destino STOMP: /app/telemetry/heartbeat
     */
    @MessageMapping("/telemetry/heartbeat")
    public void handleHeartbeat(@Payload HeartbeatPayload payload) {
        log.debug("Heartbeat recibido del nodo {} en sesión {}", payload.playerUuid(), payload.sessionId());
        redisRepository.recordHeartbeat(
            payload.sessionId(),
            payload.playerUuid(),
            payload.bleNearbyCount()
        );
    }

    /**
     * Recepción de reporte de exposición BLE prolongada (>10s a <2.8m).
     * Destino STOMP: /app/exposure/report
     */
    @MessageMapping("/exposure/report")
    public void handleExposureReport(@Payload ExposureReportPayload payload) {
        log.info("Reporte de exposición recibido: nodo {} cerca de beacon {} por {}s",
            payload.healthyPlayerUuid(), payload.infectedBeaconUuid(), payload.exposureDurationSeconds());

        InfectionService.InfectionResult result = infectionService.processExposureReportDetailed(payload);

        if (result.infected()) {
            StateChangeMessage stateChange = new StateChangeMessage(
                result.newlyInfectedUuid(),
                PlayerStatus.INFECTED.name(),
                result.carrierUuid(),
                "TACHYCARDIA_FAST",
                Instant.now().toString()
            );

            // Doble canal de entrega: Topic dedicado por nodo y cola de usuario Unicast
            messagingTemplate.convertAndSend("/topic/player/" + result.newlyInfectedUuid() + "/state", stateChange);
            messagingTemplate.convertAndSendToUser(
                result.newlyInfectedUuid(),
                "/queue/state",
                stateChange
            );

            log.info("Notificación de STATE_CHANGE (INFECTED) despachada al nodo {}", result.newlyInfectedUuid());

            // Si hay sesión asociada, emitir actualización inmediata del tablero y alertas de 50%/100%
            if (result.sessionId() != null) {
                String alertType = null;
                String alertMessage = null;
                String sessionStatus = SessionStatus.ACTIVE.name();

                if (result.reached100Percent()) {
                    alertType = "INFECTED_100_PERCENT";
                    alertMessage = "¡BROTE TOTAL! El 100% de la población ha sido infectada. Fin de la simulación.";
                    sessionStatus = SessionStatus.COMPLETED.name();
                } else if (result.reached50Percent()) {
                    alertType = "INFECTED_50_PERCENT";
                    alertMessage = "⚠️ ¡ALERTA EPIDEMIOLÓGICA! Más del 50% de la población se encuentra infectada.";
                }

                BoardUpdateMessage boardUpdate = new BoardUpdateMessage(
                    result.sessionId(),
                    (double) result.totalPlayers(),
                    1.0,
                    0.05,
                    result.totalInfected(),
                    result.totalHealthy(),
                    (double) result.totalInfected(),
                    Instant.now().toEpochMilli(),
                    sessionStatus,
                    result.totalPlayers(),
                    3,
                    result.totalPlayers() >= 3,
                    alertType,
                    alertMessage
                );

                messagingTemplate.convertAndSend("/topic/board/" + result.sessionId(), boardUpdate);
                log.info("BoardUpdate inmediato emitido tras contagio en sesión {}: {} infectados / {} sanos (alerta: {})",
                    result.sessionId(), result.totalInfected(), result.totalHealthy(), alertType);
            }
        }
    }
}
