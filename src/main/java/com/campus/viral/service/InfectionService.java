package com.campus.viral.service;

import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.domain.SessionStatus;
import com.campus.viral.model.dto.request.ExposureReportPayload;
import com.campus.viral.model.entity.GameSession;
import com.campus.viral.model.entity.InfectionAuditLog;
import com.campus.viral.model.entity.PlayerNode;
import com.campus.viral.repository.jpa.GameSessionRepository;
import com.campus.viral.repository.jpa.InfectionAuditLogRepository;
import com.campus.viral.repository.jpa.PlayerNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class InfectionService {

    private static final Logger log = LoggerFactory.getLogger(InfectionService.class);

    private final PlayerNodeRepository playerRepository;
    private final InfectionAuditLogRepository auditLogRepository;
    private final GameSessionRepository sessionRepository;

    public record InfectionResult(
        boolean infected,
        String newlyInfectedUuid,
        String carrierUuid,
        Long sessionId,
        long totalInfected,
        long totalHealthy,
        long totalPlayers,
        boolean reached50Percent,
        boolean reached100Percent
    ) {
        public static InfectionResult noInfection() {
            return new InfectionResult(false, null, null, null, 0, 0, 0, false, false);
        }
    }

    public InfectionService(PlayerNodeRepository playerRepository,
                            InfectionAuditLogRepository auditLogRepository,
                            GameSessionRepository sessionRepository) {
        this.playerRepository = playerRepository;
        this.auditLogRepository = auditLogRepository;
        this.sessionRepository = sessionRepository;
    }

    public InfectionService(PlayerNodeRepository playerRepository,
                            InfectionAuditLogRepository auditLogRepository) {
        this(playerRepository, auditLogRepository, null);
    }

    /**
     * Procesa un reporte de exposición Bluetooth aplicando las reglas de negocio RN04 y RN05 de forma simétrica.
     * Si cualquiera de los dos dispositivos es portador (INFECTED o PATIENT_ZERO) y el otro es HEALTHY,
     * se efectúa el contagio atómico del nodo vulnerable.
     */
    @Transactional
    public InfectionResult processExposureReportDetailed(ExposureReportPayload payload) {
        // Descartar autorreporte
        if (payload.healthyPlayerUuid() == null || payload.infectedBeaconUuid() == null ||
            payload.healthyPlayerUuid().equalsIgnoreCase(payload.infectedBeaconUuid())) {
            return InfectionResult.noInfection();
        }

        // Regla RN04 con tolerancia para radiofrecuencia (duración >= 10s y distancia <= 2.8m)
        if (payload.exposureDurationSeconds() < 10 || payload.estimatedDistanceMeters() > 2.8) {
            log.debug("Reporte ignorado: no cumple umbrales de proximidad RN04 (duración={}, distancia={})",
                payload.exposureDurationSeconds(), payload.estimatedDistanceMeters());
            return InfectionResult.noInfection();
        }

        // Búsqueda de ambos participantes (soporta UUID completo o prefijo BLE de baliza)
        Optional<PlayerNode> nodeAOpt = playerRepository.findByPlayerUuidOrPrefix(payload.healthyPlayerUuid())
            .stream().findFirst();
        Optional<PlayerNode> nodeBOpt = playerRepository.findByPlayerUuidOrPrefix(payload.infectedBeaconUuid())
            .stream().findFirst();

        if (nodeAOpt.isEmpty()) {
            log.warn("Dispositivo A no encontrado en el sistema: {}", payload.healthyPlayerUuid());
            return InfectionResult.noInfection();
        }
        if (nodeBOpt.isEmpty()) {
            log.warn("Dispositivo B no encontrado en el sistema: {}", payload.infectedBeaconUuid());
            return InfectionResult.noInfection();
        }

        PlayerNode nodeA = nodeAOpt.get();
        PlayerNode nodeB = nodeBOpt.get();

        boolean aIsCarrier = (nodeA.getStatus() == PlayerStatus.INFECTED || nodeA.getStatus() == PlayerStatus.PATIENT_ZERO);
        boolean bIsCarrier = (nodeB.getStatus() == PlayerStatus.INFECTED || nodeB.getStatus() == PlayerStatus.PATIENT_ZERO);

        PlayerNode carrier;
        PlayerNode victim;

        if (bIsCarrier && nodeA.getStatus() == PlayerStatus.HEALTHY) {
            carrier = nodeB;
            victim = nodeA;
        } else if (aIsCarrier && nodeB.getStatus() == PlayerStatus.HEALTHY) {
            carrier = nodeA;
            victim = nodeB;
        } else {
            log.debug("No hay condición de contagio entre {} ({}) y {} ({})",
                nodeA.getPlayerUuid(), nodeA.getStatus(), nodeB.getPlayerUuid(), nodeB.getStatus());
            return InfectionResult.noInfection();
        }

        // Ejecutar actualización atómica en base de datos.
        int updatedRows = playerRepository.markInfectedIfHealthy(
            victim.getPlayerUuid(),
            PlayerStatus.INFECTED,
            Instant.now()
        );

        if (updatedRows > 0) {
            log.info("CONTAGIO CONFIRMADO (Simétrico): Jugador {} infectado por portador {}",
                victim.getPlayerUuid(), carrier.getPlayerUuid());

            GameSession session = victim.getSession();
            Long sessionId = session != null ? session.getId() : payload.sessionId();

            // Registrar log de auditoría histórica
            InfectionAuditLog audit = new InfectionAuditLog(
                session,
                victim.getPlayerUuid(),
                carrier.getPlayerUuid(),
                payload.exposureDurationSeconds(),
                payload.estimatedDistanceMeters()
            );
            auditLogRepository.save(audit);

            // Calcular conteos globales de la sesión para alertas de 50% y 100%
            long totalInfected = sessionId != null ? playerRepository.countBySessionIdAndStatusIn(
                sessionId, List.of(PlayerStatus.PATIENT_ZERO, PlayerStatus.INFECTED)
            ) : 0;
            long totalHealthy = sessionId != null ? playerRepository.countBySessionIdAndStatusIn(
                sessionId, List.of(PlayerStatus.HEALTHY)
            ) : 0;
            long totalPlayers = totalInfected + totalHealthy;

            boolean reached50Percent = false;
            boolean reached100Percent = false;

            if (session != null && totalPlayers >= 3) {
                if (totalInfected >= totalPlayers) {
                    reached100Percent = true;
                    session.setStatus(SessionStatus.COMPLETED);
                    if (sessionRepository != null) {
                        sessionRepository.save(session);
                    }
                    log.warn("¡BROTE TOTAL (100%) alcanzado en sesión {}! Simulación concluida automáticamente.", sessionId);
                } else if (totalInfected >= Math.ceil(totalPlayers * 0.5) && !session.isInflectionAlertSent()) {
                    reached50Percent = true;
                    session.setInflectionAlertSent(true);
                    if (sessionRepository != null) {
                        sessionRepository.save(session);
                    }
                    log.warn("¡ALERTA EPIDEMIOLÓGICA (50%) alcanzada en sesión {}! (Infectados={}/{})",
                        sessionId, totalInfected, totalPlayers);
                }
            }

            return new InfectionResult(
                true,
                victim.getPlayerUuid(),
                carrier.getPlayerUuid(),
                sessionId,
                totalInfected,
                totalHealthy,
                totalPlayers,
                reached50Percent,
                reached100Percent
            );
        }

        return InfectionResult.noInfection();
    }

    /**
     * Versión simplificada que retorna booleano para compatibilidad hacia atrás.
     */
    @Transactional
    public boolean processExposureReport(ExposureReportPayload payload) {
        return processExposureReportDetailed(payload).infected();
    }
}
