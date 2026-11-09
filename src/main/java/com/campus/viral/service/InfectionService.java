package com.campus.viral.service;

import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.dto.request.ExposureReportPayload;
import com.campus.viral.model.entity.InfectionAuditLog;
import com.campus.viral.model.entity.PlayerNode;
import com.campus.viral.repository.jpa.InfectionAuditLogRepository;
import com.campus.viral.repository.jpa.PlayerNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class InfectionService {

    private static final Logger log = LoggerFactory.getLogger(InfectionService.class);

    private final PlayerNodeRepository playerRepository;
    private final InfectionAuditLogRepository auditLogRepository;

    public InfectionService(PlayerNodeRepository playerRepository,
                            InfectionAuditLogRepository auditLogRepository) {
        this.playerRepository = playerRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Procesa un reporte de exposición Bluetooth aplicando las reglas de negocio RN04 y RN05.
     * Retorna true si el jugador fue efectivamente infectado en esta transacción.
     */
    @Transactional
    public boolean processExposureReport(ExposureReportPayload payload) {
        // Regla RN04: Duración mínima continua >= 15s y distancia estimada <= 2.0m
        if (payload.exposureDurationSeconds() < 15 || payload.estimatedDistanceMeters() > 2.0) {
            log.debug("Reporte ignorado: no cumple umbrales de proximidad RN04 (duración={}, distancia={})",
                payload.exposureDurationSeconds(), payload.estimatedDistanceMeters());
            return false;
        }

        // Validar existencia y estado del transmisor (debe ser INFECTED o PATIENT_ZERO)
        Optional<PlayerNode> transmitterOpt = playerRepository.findByPlayerUuid(payload.infectedBeaconUuid());
        if (transmitterOpt.isEmpty()) {
            log.warn("Transmisor no encontrado en el sistema: {}", payload.infectedBeaconUuid());
            return false;
        }

        PlayerNode transmitter = transmitterOpt.get();
        if (transmitter.getStatus() != PlayerStatus.INFECTED && transmitter.getStatus() != PlayerStatus.PATIENT_ZERO) {
            log.warn("El transmisor {} reportado no tiene estado infectado ({})",
                transmitter.getPlayerUuid(), transmitter.getStatus());
            return false;
        }

        // Ejecutar actualización atómica en base de datos.
        // Si el jugador ya era IMMUNE (RN05) o ya estaba INFECTED, updatedRows será 0.
        int updatedRows = playerRepository.markInfectedIfHealthy(
            payload.healthyPlayerUuid(),
            PlayerStatus.INFECTED,
            Instant.now()
        );

        if (updatedRows > 0) {
            log.info("CONTAGIO CONFIRMADO: Jugador {} infectado por beacon {}",
                payload.healthyPlayerUuid(), payload.infectedBeaconUuid());

            // Registrar log de auditoría histórica
            InfectionAuditLog audit = new InfectionAuditLog(
                transmitter.getSession(),
                payload.healthyPlayerUuid(),
                payload.infectedBeaconUuid(),
                payload.exposureDurationSeconds(),
                payload.estimatedDistanceMeters()
            );
            auditLogRepository.save(audit);
            return true;
        }

        return false;
    }
}
