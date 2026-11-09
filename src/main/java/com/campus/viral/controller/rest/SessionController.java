package com.campus.viral.controller.rest;

import com.campus.viral.exception.ResourceNotFoundException;
import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.domain.SessionStatus;
import com.campus.viral.model.dto.request.CreateSessionRequest;
import com.campus.viral.model.dto.request.JoinSessionRequest;
import com.campus.viral.model.dto.response.JoinResponse;
import com.campus.viral.model.dto.response.SessionResponse;
import com.campus.viral.model.entity.GameSession;
import com.campus.viral.model.entity.PlayerNode;
import com.campus.viral.repository.jpa.GameSessionRepository;
import com.campus.viral.repository.jpa.PlayerNodeRepository;
import com.campus.viral.repository.redis.ActivePlayerRedisRepository;
import com.campus.viral.service.MathService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final GameSessionRepository sessionRepository;
    private final PlayerNodeRepository playerRepository;
    private final ActivePlayerRedisRepository redisRepository;
    private final MathService mathService;

    public SessionController(GameSessionRepository sessionRepository,
                             PlayerNodeRepository playerRepository,
                             ActivePlayerRedisRepository redisRepository,
                             MathService mathService) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.redisRepository = redisRepository;
        this.mathService = mathService;
    }

    /**
     * RF01: Crea una nueva partida y define la condición inicial P(0).
     */
    @PostMapping
    @Transactional
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        Optional<GameSession> existing = sessionRepository.findBySessionCode(request.sessionCode());
        if (existing.isPresent()) {
            GameSession s = existing.get();
            return ResponseEntity.ok(
                new SessionResponse(
                    s.getId(),
                    s.getSessionCode(),
                    s.getStatus(),
                    s.getInitialInfectedP0(),
                    s.getCarryingCapacityK(),
                    s.getIntegrationConstantA()
                )
            );
        }

        double initialK = request.initialP0() * 2.0; // Base inicial para k
        double initialA = mathService.calculateInitialA(initialK, request.initialP0());

        GameSession session = new GameSession(
            request.sessionCode(),
            SessionStatus.ACTIVE,
            request.initialP0(),
            initialK,
            initialA,
            Instant.now()
        );

        GameSession saved = sessionRepository.save(session);

        return ResponseEntity.status(HttpStatus.CREATED).body(
            new SessionResponse(
                saved.getId(),
                saved.getSessionCode(),
                saved.getStatus(),
                saved.getInitialInfectedP0(),
                saved.getCarryingCapacityK(),
                saved.getIntegrationConstantA()
            )
        );
    }

    /**
     * RF01 / RN01: Registra un dispositivo móvil en la sesión y lo suma a 'k' en Redis.
     */
    @PostMapping("/{id}/join")
    @Transactional
    public ResponseEntity<JoinResponse> joinSession(
        @PathVariable("id") Long id,
        @Valid @RequestBody JoinSessionRequest request
    ) {
        GameSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con ID: " + id));

        // Verificar si el dispositivo ya estaba registrado en esta sesión
        Optional<PlayerNode> existingPlayer = playerRepository.findBySessionIdAndDeviceMacHash(id, request.deviceMacHash());
        if (existingPlayer.isPresent()) {
            PlayerNode player = existingPlayer.get();
            if (request.fcmToken() != null && !request.fcmToken().isBlank()) {
                player.setFcmToken(request.fcmToken());
                playerRepository.save(player);
            }
            redisRepository.recordHeartbeat(id, player.getPlayerUuid(), 0);
            return ResponseEntity.ok(new JoinResponse(
                player.getPlayerUuid(),
                id,
                player.getStatus(),
                player.getCreatedAt()
            ));
        }

        // Asignación de rol: Paciente Cero si aún no se ha alcanzado P(0)
        long currentInfected = playerRepository.countBySessionIdAndStatusIn(
            id, List.of(PlayerStatus.PATIENT_ZERO, PlayerStatus.INFECTED)
        );

        PlayerStatus assignedStatus = (currentInfected < session.getInitialInfectedP0())
            ? PlayerStatus.PATIENT_ZERO
            : PlayerStatus.HEALTHY;

        String playerUuid = UUID.randomUUID().toString();
        PlayerNode player = new PlayerNode(
            session,
            request.deviceMacHash(),
            playerUuid,
            assignedStatus,
            request.fcmToken(),
            assignedStatus == PlayerStatus.PATIENT_ZERO ? Instant.now() : null
        );

        playerRepository.save(player);

        // Registrar inmediatamente en Redis para que compute dentro del límite poblacional 'k'
        redisRepository.recordHeartbeat(id, playerUuid, 0);

        return ResponseEntity.status(HttpStatus.CREATED).body(
            new JoinResponse(
                playerUuid,
                id,
                assignedStatus,
                player.getCreatedAt()
            )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable("id") Long id) {
        GameSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con ID: " + id));

        return ResponseEntity.ok(
            new SessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getStatus(),
                session.getInitialInfectedP0(),
                session.getCarryingCapacityK(),
                session.getIntegrationConstantA()
            )
        );
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "viral-simulation-backend",
            "timestamp", Instant.now().toString()
        ));
    }
}
