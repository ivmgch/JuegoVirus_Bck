package com.campus.viral.controller.ws;

import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.dto.request.ExposureReportPayload;
import com.campus.viral.model.dto.request.HeartbeatPayload;
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
     * Recepción de reporte de exposición BLE prolongada (>15s a <2m).
     * Destino STOMP: /app/exposure/report
     */
    @MessageMapping("/exposure/report")
    public void handleExposureReport(@Payload ExposureReportPayload payload) {
        log.info("Reporte de exposición recibido: {} expuesto a beacon {} por {}s",
            payload.healthyPlayerUuid(), payload.infectedBeaconUuid(), payload.exposureDurationSeconds());

        boolean infected = infectionService.processExposureReport(payload);

        if (infected) {
            StateChangeMessage stateChange = new StateChangeMessage(
                payload.healthyPlayerUuid(),
                PlayerStatus.INFECTED.name(),
                payload.infectedBeaconUuid(),
                Instant.now().toString()
            );

            // Doble canal de entrega: Topic dedicado por nodo y cola de usuario Unicast
            messagingTemplate.convertAndSend("/topic/player/" + payload.healthyPlayerUuid() + "/state", stateChange);
            messagingTemplate.convertAndSendToUser(
                payload.healthyPlayerUuid(),
                "/queue/state",
                stateChange
            );

            log.info("Notificación de STATE_CHANGE (INFECTED) despachada al nodo {}", payload.healthyPlayerUuid());
        }
    }
}
