package com.campus.viral.service;

import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.domain.SessionStatus;
import com.campus.viral.model.dto.request.ExposureReportPayload;
import com.campus.viral.model.entity.GameSession;
import com.campus.viral.model.entity.PlayerNode;
import com.campus.viral.repository.jpa.InfectionAuditLogRepository;
import com.campus.viral.repository.jpa.PlayerNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfectionServiceTest {

    @Mock
    private PlayerNodeRepository playerRepository;

    @Mock
    private InfectionAuditLogRepository auditLogRepository;

    private InfectionService infectionService;

    private GameSession session;
    private PlayerNode patientZero;
    private PlayerNode healthyPlayer;

    @BeforeEach
    void setUp() {
        infectionService = new InfectionService(playerRepository, auditLogRepository);

        session = new GameSession("CAMPUS-TEST", SessionStatus.ACTIVE, 1, 3.0, 2.0, Instant.now());
        session.setId(1L);

        patientZero = new PlayerNode(session, "mac-zero", "uuid-patient-zero-1234567890",
            PlayerStatus.PATIENT_ZERO, null, Instant.now());

        healthyPlayer = new PlayerNode(session, "mac-healthy", "uuid-healthy-player-9876543210",
            PlayerStatus.HEALTHY, null, null);
    }

    @Test
    @DisplayName("Debe confirmar contagio cuando la exposición cumple RN04 y el transmisor es PATIENT_ZERO")
    void testSuccessfulInfectionFromPatientZero() {
        when(playerRepository.findByPlayerUuidOrPrefix("uuid-patient-zero-1234567890"))
            .thenReturn(List.of(patientZero));

        when(playerRepository.markInfectedIfHealthy(eq("uuid-healthy-player-9876543210"), eq(PlayerStatus.INFECTED), any()))
            .thenReturn(1);

        ExposureReportPayload payload = new ExposureReportPayload(
            1L,
            "uuid-healthy-player-9876543210",
            "uuid-patient-zero-1234567890",
            16,
            1.2
        );

        boolean result = infectionService.processExposureReport(payload);

        assertTrue(result, "El reporte válido debe resultar en contagio confirmado");
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Debe confirmar contagio cuando el beacon se busca por prefijo de 16 caracteres")
    void testSuccessfulInfectionWithBeaconPrefix() {
        String beaconPrefix = "uuid-patient-zer";
        when(playerRepository.findByPlayerUuidOrPrefix(beaconPrefix))
            .thenReturn(List.of(patientZero));

        when(playerRepository.markInfectedIfHealthy(eq("uuid-healthy-player-9876543210"), eq(PlayerStatus.INFECTED), any()))
            .thenReturn(1);

        ExposureReportPayload payload = new ExposureReportPayload(
            1L,
            "uuid-healthy-player-9876543210",
            beaconPrefix,
            18,
            0.8
        );

        boolean result = infectionService.processExposureReport(payload);

        assertTrue(result);
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Debe rechazar contagio si el transmisor es HEALTHY")
    void testRejectWhenTransmitterIsHealthy() {
        PlayerNode anotherHealthy = new PlayerNode(session, "mac-2", "uuid-another-healthy",
            PlayerStatus.HEALTHY, null, null);

        when(playerRepository.findByPlayerUuidOrPrefix("uuid-another-healthy"))
            .thenReturn(List.of(anotherHealthy));

        ExposureReportPayload payload = new ExposureReportPayload(
            1L,
            "uuid-healthy-player-9876543210",
            "uuid-another-healthy",
            20,
            1.0
        );

        boolean result = infectionService.processExposureReport(payload);

        assertFalse(result, "No puede haber contagio si el transmisor no está infectado");
        verify(playerRepository, never()).markInfectedIfHealthy(any(), any(), any());
    }

    @Test
    @DisplayName("Debe rechazar reporte si la duración es menor a 15 segundos")
    void testRejectInsufficientDuration() {
        ExposureReportPayload payload = new ExposureReportPayload(
            1L,
            "uuid-healthy-player-9876543210",
            "uuid-patient-zero-1234567890",
            14, // < 15s
            1.0
        );

        boolean result = infectionService.processExposureReport(payload);

        assertFalse(result);
        verify(playerRepository, never()).findByPlayerUuidOrPrefix(any());
    }

    @Test
    @DisplayName("Debe rechazar reporte si la distancia excede 2.2 metros")
    void testRejectExcessiveDistance() {
        ExposureReportPayload payload = new ExposureReportPayload(
            1L,
            "uuid-healthy-player-9876543210",
            "uuid-patient-zero-1234567890",
            25,
            3.5 // > 2.2m
        );

        boolean result = infectionService.processExposureReport(payload);

        assertFalse(result);
        verify(playerRepository, never()).findByPlayerUuidOrPrefix(any());
    }

    @Test
    @DisplayName("Debe descartar autoreporte si healthyPlayerUuid coincide con infectedBeaconUuid")
    void testRejectSelfInfection() {
        ExposureReportPayload payload = new ExposureReportPayload(
            1L,
            "uuid-patient-zero-1234567890",
            "uuid-patient-zero-1234567890",
            30,
            0.5
        );

        boolean result = infectionService.processExposureReport(payload);

        assertFalse(result);
        verify(playerRepository, never()).findByPlayerUuidOrPrefix(any());
    }
}
