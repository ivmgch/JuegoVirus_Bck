package com.campus.viral.scheduler;

import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.domain.SessionStatus;
import com.campus.viral.model.dto.response.BoardUpdateMessage;
import com.campus.viral.model.entity.GameSession;
import com.campus.viral.repository.jpa.GameSessionRepository;
import com.campus.viral.repository.jpa.PlayerNodeRepository;
import com.campus.viral.repository.redis.ActivePlayerRedisRepository;
import com.campus.viral.service.FcmNotificationService;
import com.campus.viral.service.MathService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class GameSimulationScheduler {

    private static final Logger log = LoggerFactory.getLogger(GameSimulationScheduler.class);

    private final GameSessionRepository sessionRepository;
    private final PlayerNodeRepository playerRepository;
    private final ActivePlayerRedisRepository redisRepository;
    private final MathService mathService;
    private final FcmNotificationService fcmNotificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameSimulationScheduler(
        GameSessionRepository sessionRepository,
        PlayerNodeRepository playerRepository,
        ActivePlayerRedisRepository redisRepository,
        MathService mathService,
        FcmNotificationService fcmNotificationService,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.redisRepository = redisRepository;
        this.mathService = mathService;
        this.fcmNotificationService = fcmNotificationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Ciclo de simulación cada 5 segundos:
     * - Para partidas en espera (LOBBY): Sincroniza el conteo de jugadores conectados.
     * - Para partidas activas: Recalcula las variables del modelo logístico (k, A, r, P(t)).
     * - Emite BOARD_UPDATE por WebSocket a todos los clientes.
     * - Evalúa el punto de inflexión poblacional (k / 2) para disparar la alerta FCM.
     */
    @Scheduled(fixedRateString = "${app.simulation.scheduler-rate-ms:5000}")
    @Transactional
    public void executeSimulationCycle() {
        Instant now = Instant.now();

        // 1. Sincronizar sesiones en sala de espera (LOBBY)
        List<GameSession> waitingSessions = sessionRepository.findByStatus(SessionStatus.WAITING_FOR_PLAYERS);
        for (GameSession session : waitingSessions) {
            Long sessionId = session.getId();
            if (sessionId == null) continue;

            long totalPlayers = playerRepository.countBySessionId(sessionId);
            BoardUpdateMessage lobbyUpdate = new BoardUpdateMessage(
                sessionId,
                (double) totalPlayers,
                session.getIntegrationConstantA(),
                session.getGrowthRateR(),
                0,
                totalPlayers,
                0.0,
                now.toEpochMilli(),
                SessionStatus.WAITING_FOR_PLAYERS.name(),
                totalPlayers,
                3,
                totalPlayers >= 3
            );
            messagingTemplate.convertAndSend("/topic/board/" + sessionId, lobbyUpdate);
        }

        // 2. Ejecutar modelo logístico continuo para partidas activas
        List<GameSession> activeSessions = sessionRepository.findByStatus(SessionStatus.ACTIVE);
        for (GameSession session : activeSessions) {
            Long sessionId = session.getId();
            if (sessionId == null) continue;

            Instant startedAt = session.getStartedAt() != null ? session.getStartedAt() : now;
            double tElapsedSeconds = (double) Duration.between(startedAt, now).getSeconds();

            // 1. Obtener 'k' dinámico desde Redis (Heartbeats activos en los últimos 2 minutos)
            double currentK = redisRepository.getActiveCarryingCapacityK(sessionId);

            if (currentK <= 0.0) {
                currentK = Math.max(session.getCarryingCapacityK(), session.getInitialInfectedP0());
            }

            // 2. Conteo real de infectados y sanos en base de datos
            long realInfectedCount = playerRepository.countBySessionIdAndStatusIn(
                sessionId, List.of(PlayerStatus.PATIENT_ZERO, PlayerStatus.INFECTED)
            );
            long realHealthyCount = playerRepository.countBySessionIdAndStatusIn(
                sessionId, List.of(PlayerStatus.HEALTHY)
            );
            long totalPlayers = realInfectedCount + realHealthyCount;

            // 3. Obtener densidad BLE acumulada en Redis para computar 'r' dinámico
            long bleInteractions = redisRepository.getRecentBleInteractionsCount(sessionId);
            double dynamicR = mathService.calculateDynamicR(currentK, bleInteractions);

            // 4. Gestión de la constante de integración 'A':
            // En el modelo logístico P(t) = k / (A*e^(-rt) + 1), 'A' se evalúa en t=0 como (k - P0) / P0.
            // Para mantener la consistencia teórica de la ecuación de Verhulst, 'A' se mantiene constante
            // y solo se recalcula si la capacidad 'k' varía bruscamente (entrada o salida de participantes).
            double previousK = session.getCarryingCapacityK();
            double currentA = session.getIntegrationConstantA();
            if (currentA <= 0.0) {
                currentA = mathService.calculateInitialA(currentK, Math.max(1, session.getInitialInfectedP0()));
            }

            if (previousK > 0 && Math.abs(currentK - previousK) >= 1.0) {
                // k varió bruscamente: recalcular A preservando el valor P(t) alcanzado para evitar saltos discretos
                double currentPt = mathService.solveLogistic(previousK, currentA, dynamicR, tElapsedSeconds);
                currentA = mathService.recalculateSmoothedA(currentK, currentPt, dynamicR, tElapsedSeconds);
            }

            // 5. Resolver P(t) teórico evaluando la solución analítica continua de la ecuación diferencial
            double theoreticalPt = mathService.solveLogistic(currentK, currentA, dynamicR, tElapsedSeconds);

            // 6. Actualizar las constantes de la sesión en base de datos
            session.setCarryingCapacityK(currentK);
            session.setIntegrationConstantA(currentA);
            session.setGrowthRateR(dynamicR);
            sessionRepository.save(session);

            // 7. Emitir BROADCAST cada 5 segundos al tópico de la sesión (/topic/board/{sessionId})
            BoardUpdateMessage boardUpdate = new BoardUpdateMessage(
                sessionId,
                currentK,
                currentA,
                dynamicR,
                realInfectedCount,
                realHealthyCount,
                theoreticalPt,
                now.toEpochMilli(),
                SessionStatus.ACTIVE.name(),
                totalPlayers,
                3,
                true
            );

            messagingTemplate.convertAndSend("/topic/board/" + sessionId, boardUpdate);

            // 8. Evaluar Alerta Crítica (Población infectada >= k / 2)
            if (!session.isInflectionAlertSent() && mathService.isInflectionPointReached((double) realInfectedCount, currentK)) {
                log.warn("¡ALERTA CRÍTICA! Sesión {} alcanzó el punto de inflexión k/2 (Infectados={}, k={})",
                    sessionId, realInfectedCount, currentK);

                List<String> healthyTokens = playerRepository.findHealthyFcmTokensBySessionId(sessionId);
                fcmNotificationService.sendCriticalInflectionAlert(healthyTokens, currentK, realInfectedCount);

                session.setInflectionAlertSent(true);
                sessionRepository.save(session);
            }
        }
    }
}
