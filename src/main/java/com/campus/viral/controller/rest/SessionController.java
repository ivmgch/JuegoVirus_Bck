package com.campus.viral.controller.rest;

import com.campus.viral.exception.BusinessRuleException;
import com.campus.viral.exception.ResourceNotFoundException;
import com.campus.viral.model.domain.PlayerStatus;
import com.campus.viral.model.domain.SessionStatus;
import com.campus.viral.model.dto.request.CreateSessionRequest;
import com.campus.viral.model.dto.request.JoinSessionRequest;
import com.campus.viral.model.dto.response.BoardUpdateMessage;
import com.campus.viral.model.dto.response.JoinResponse;
import com.campus.viral.model.dto.response.SessionResponse;
import com.campus.viral.model.dto.response.StateChangeMessage;
import com.campus.viral.model.entity.GameSession;
import com.campus.viral.model.entity.PlayerNode;
import com.campus.viral.repository.jpa.GameSessionRepository;
import com.campus.viral.repository.jpa.PlayerNodeRepository;
import com.campus.viral.repository.redis.ActivePlayerRedisRepository;
import com.campus.viral.service.MathService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    public static final int MIN_PLAYERS_REQUIRED = 3;

    private final GameSessionRepository sessionRepository;
    private final PlayerNodeRepository playerRepository;
    private final ActivePlayerRedisRepository redisRepository;
    private final MathService mathService;
    private final SimpMessagingTemplate messagingTemplate;

    public SessionController(GameSessionRepository sessionRepository,
                             PlayerNodeRepository playerRepository,
                             ActivePlayerRedisRepository redisRepository,
                             MathService mathService,
                             SimpMessagingTemplate messagingTemplate) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.redisRepository = redisRepository;
        this.mathService = mathService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * RF01: Crea una nueva partida en estado WAITING_FOR_PLAYERS.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        Optional<GameSession> existing = sessionRepository.findBySessionCode(request.sessionCode());
        GameSession session;
        if (existing.isPresent()) {
            session = existing.get();
            // Limpiar jugadores anteriores/huérfanos de partidas previas para garantizar una sala 100% limpia
            List<PlayerNode> oldPlayers = playerRepository.findBySessionId(session.getId());
            if (!oldPlayers.isEmpty()) {
                for (PlayerNode p : oldPlayers) {
                    redisRepository.removePlayer(session.getId(), p.getPlayerUuid());
                }
                playerRepository.deleteAll(oldPlayers);
            }
            session.setStatus(SessionStatus.WAITING_FOR_PLAYERS);
            session.setInitialInfectedP0(Math.max(1, request.initialP0()));
            session.setCarryingCapacityK(0.0);
            session.setIntegrationConstantA(1.0);
            session.setStartedAt(null);
            session.setInflectionAlertSent(false);
            session = sessionRepository.save(session);
        } else {
            // Se inicia en sala de espera hasta que se conecten al menos 3 jugadores
            session = new GameSession(
                request.sessionCode(),
                SessionStatus.WAITING_FOR_PLAYERS,
                Math.max(1, request.initialP0()),
                0.0,
                1.0,
                null
            );
            session = sessionRepository.save(session);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(
            new SessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getStatus(),
                session.getInitialInfectedP0(),
                session.getCarryingCapacityK(),
                session.getIntegrationConstantA(),
                0,
                MIN_PLAYERS_REQUIRED,
                false,
                List.of()
            )
        );
    }

    /**
     * RF01 / RN01: Registra un dispositivo móvil en la sesión (Lobby o Partida activa).
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
        PlayerNode player;
        if (existingPlayer.isPresent()) {
            player = existingPlayer.get();
            if (request.fcmToken() != null && !request.fcmToken().isBlank()) {
                player.setFcmToken(request.fcmToken());
                playerRepository.save(player);
            }
        } else {
            // Si la partida ya está activa, aplicar rol de acuerdo a p0, sino entra como HEALTHY a la sala de espera
            PlayerStatus initialStatus = PlayerStatus.HEALTHY;
            if (session.getStatus() == SessionStatus.ACTIVE) {
                long currentInfected = playerRepository.countBySessionIdAndStatusIn(
                    id, List.of(PlayerStatus.PATIENT_ZERO, PlayerStatus.INFECTED)
                );
                if (currentInfected < session.getInitialInfectedP0()) {
                    initialStatus = PlayerStatus.PATIENT_ZERO;
                }
            }

            String playerUuid = UUID.randomUUID().toString();
            player = new PlayerNode(
                session,
                request.deviceMacHash(),
                playerUuid,
                initialStatus,
                request.fcmToken(),
                initialStatus == PlayerStatus.PATIENT_ZERO ? Instant.now() : null
            );
            playerRepository.save(player);
        }

        // Registrar inmediatamente en Redis
        redisRepository.recordHeartbeat(id, player.getPlayerUuid(), 0);

        // Notificar en tiempo real a todos los clientes suscritos al tablero sobre el nuevo jugador en la sala
        List<PlayerNode> allPlayers = playerRepository.findBySessionId(id);
        long totalCount = allPlayers.size();
        long infectedCount = allPlayers.stream().filter(p -> p.getStatus() == PlayerStatus.PATIENT_ZERO || p.getStatus() == PlayerStatus.INFECTED).count();
        long healthyCount = totalCount - infectedCount;

        BoardUpdateMessage lobbyUpdate = new BoardUpdateMessage(
            id,
            session.getStatus() == SessionStatus.ACTIVE ? session.getCarryingCapacityK() : totalCount,
            session.getIntegrationConstantA(),
            session.getGrowthRateR(),
            infectedCount,
            healthyCount,
            (double) infectedCount,
            System.currentTimeMillis(),
            session.getStatus().name(),
            totalCount,
            MIN_PLAYERS_REQUIRED,
            totalCount >= MIN_PLAYERS_REQUIRED
        );
        messagingTemplate.convertAndSend("/topic/board/" + id, lobbyUpdate);

        return ResponseEntity.ok(
            new JoinResponse(
                player.getPlayerUuid(),
                id,
                player.getStatus(),
                player.getCreatedAt()
            )
        );
    }

    /**
     * Inicia formalmente la partida de simulación cuando hay al menos 3 jugadores.
     */
    @PostMapping("/{id}/start")
    @Transactional
    public ResponseEntity<SessionResponse> startSession(@PathVariable("id") Long id) {
        GameSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con ID: " + id));

        List<PlayerNode> players = playerRepository.findBySessionId(id);
        long totalPlayers = players.size();

        if (totalPlayers < MIN_PLAYERS_REQUIRED) {
            throw new BusinessRuleException(
                "No se puede iniciar la partida: se requieren al menos " + MIN_PLAYERS_REQUIRED +
                " jugadores conectados. Actualmente hay " + totalPlayers + "."
            );
        }

        if (session.getStatus() != SessionStatus.ACTIVE) {
            session.setStatus(SessionStatus.ACTIVE);
            session.setStartedAt(Instant.now());

            // SORTEO ALEATORIO SECRETO DEL VIRUS:
            // Barajamos aleatoriamente la lista de participantes para que nadie sepa a quién le tocará
            List<PlayerNode> shuffledPlayers = new ArrayList<>(players);
            Collections.shuffle(shuffledPlayers, new SecureRandom());

            // Asignar Paciente(s) Cero entre los jugadores sorteados al azar
            int p0Count = Math.max(1, Math.min(session.getInitialInfectedP0(), (int) totalPlayers - 1));

            for (int i = 0; i < shuffledPlayers.size(); i++) {
                PlayerNode p = shuffledPlayers.get(i);
                if (i < p0Count) {
                    p.setStatus(PlayerStatus.PATIENT_ZERO);
                    p.setInfectedAt(Instant.now());
                } else {
                    p.setStatus(PlayerStatus.HEALTHY);
                    p.setInfectedAt(null);
                }
                playerRepository.save(p);
            }

            // Calcular k y A basados en los jugadores reales conectados
            double actualK = (double) totalPlayers;
            double actualA = mathService.calculateInitialA(actualK, p0Count);
            session.setCarryingCapacityK(actualK);
            session.setIntegrationConstantA(actualA);
            sessionRepository.save(session);

            // Notificar a cada jugador en SECRETO su rol asignado mediante unicast y topic privado por WebSocket
            for (PlayerNode p : shuffledPlayers) {
                StateChangeMessage stateChange = new StateChangeMessage(
                    p.getPlayerUuid(),
                    p.getStatus().name(),
                    null,
                    p.getStatus() == PlayerStatus.PATIENT_ZERO ? "TACHYCARDIA_FAST" : null,
                    Instant.now().toString()
                );
                messagingTemplate.convertAndSend("/topic/player/" + p.getPlayerUuid() + "/state", stateChange);
                messagingTemplate.convertAndSendToUser(p.getPlayerUuid(), "/queue/state", stateChange);
            }

            // Emitir un BoardUpdate inmediato con sessionStatus = ACTIVE para que todos pasen al tablero
            BoardUpdateMessage initialBoard = new BoardUpdateMessage(
                session.getId(),
                actualK,
                actualA,
                session.getGrowthRateR(),
                p0Count,
                totalPlayers - p0Count,
                (double) p0Count,
                Instant.now().toEpochMilli(),
                SessionStatus.ACTIVE.name(),
                totalPlayers,
                MIN_PLAYERS_REQUIRED,
                true
            );
            messagingTemplate.convertAndSend("/topic/board/" + session.getId(), initialBoard);
        }

        List<String> uuids = players.stream().map(PlayerNode::getPlayerUuid).toList();
        return ResponseEntity.ok(
            new SessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getStatus(),
                session.getInitialInfectedP0(),
                session.getCarryingCapacityK(),
                session.getIntegrationConstantA(),
                totalPlayers,
                MIN_PLAYERS_REQUIRED,
                true,
                uuids
            )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable("id") Long id) {
        GameSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con ID: " + id));

        List<PlayerNode> players = playerRepository.findBySessionId(id);
        long totalPlayers = players.size();
        List<String> uuids = players.stream().map(PlayerNode::getPlayerUuid).toList();

        return ResponseEntity.ok(
            new SessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getStatus(),
                session.getInitialInfectedP0(),
                session.getCarryingCapacityK(),
                session.getIntegrationConstantA(),
                totalPlayers,
                MIN_PLAYERS_REQUIRED,
                totalPlayers >= MIN_PLAYERS_REQUIRED,
                uuids
            )
        );
    }

    /**
     * Finaliza la partida de simulación (Host).
     */
    @PostMapping("/{id}/finish")
    @Transactional
    public ResponseEntity<SessionResponse> finishSession(@PathVariable("id") Long id) {
        GameSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con ID: " + id));

        session.setStatus(SessionStatus.COMPLETED);
        sessionRepository.save(session);

        List<PlayerNode> players = playerRepository.findBySessionId(id);
        long totalPlayers = players.size();
        long infectedCount = players.stream()
            .filter(p -> p.getStatus() == PlayerStatus.PATIENT_ZERO || p.getStatus() == PlayerStatus.INFECTED)
            .count();
        long healthyCount = totalPlayers - infectedCount;

        // Notificar a todos los dispositivos por WebSocket que la simulación terminó
        BoardUpdateMessage finishedBoard = new BoardUpdateMessage(
            session.getId(),
            session.getCarryingCapacityK(),
            session.getIntegrationConstantA(),
            session.getGrowthRateR(),
            infectedCount,
            healthyCount,
            (double) infectedCount,
            Instant.now().toEpochMilli(),
            SessionStatus.COMPLETED.name(),
            totalPlayers,
            MIN_PLAYERS_REQUIRED,
            false
        );
        messagingTemplate.convertAndSend("/topic/board/" + session.getId(), finishedBoard);

        List<String> uuids = players.stream().map(PlayerNode::getPlayerUuid).toList();
        return ResponseEntity.ok(
            new SessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getStatus(),
                session.getInitialInfectedP0(),
                session.getCarryingCapacityK(),
                session.getIntegrationConstantA(),
                totalPlayers,
                MIN_PLAYERS_REQUIRED,
                false,
                uuids
            )
        );
    }

    /**
     * Permite a un jugador salir de la partida o de la sala de espera.
     */
    @PostMapping("/{id}/leave")
    @Transactional
    public ResponseEntity<Map<String, Object>> leaveSession(
        @PathVariable("id") Long id,
        @RequestParam("playerUuid") String playerUuid
    ) {
        GameSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con ID: " + id));

        Optional<PlayerNode> playerOpt = playerRepository.findBySessionId(id).stream()
            .filter(p -> p.getPlayerUuid().equalsIgnoreCase(playerUuid))
            .findFirst();

        if (playerOpt.isPresent()) {
            PlayerNode player = playerOpt.get();
            playerRepository.delete(player);
        }

        // Remover del rastreo en Redis
        redisRepository.removePlayer(id, playerUuid);

        // Recalcular conteo de jugadores restantes
        List<PlayerNode> remainingPlayers = playerRepository.findBySessionId(id);
        long totalCount = remainingPlayers.size();
        long infectedCount = remainingPlayers.stream()
            .filter(p -> p.getStatus() == PlayerStatus.PATIENT_ZERO || p.getStatus() == PlayerStatus.INFECTED)
            .count();
        long healthyCount = totalCount - infectedCount;

        if (session.getStatus() == SessionStatus.ACTIVE) {
            session.setCarryingCapacityK((double) totalCount);
            sessionRepository.save(session);
        }

        BoardUpdateMessage updateMessage = new BoardUpdateMessage(
            id,
            session.getStatus() == SessionStatus.ACTIVE ? session.getCarryingCapacityK() : (double) totalCount,
            session.getIntegrationConstantA(),
            session.getGrowthRateR(),
            infectedCount,
            healthyCount,
            (double) infectedCount,
            System.currentTimeMillis(),
            session.getStatus().name(),
            totalCount,
            MIN_PLAYERS_REQUIRED,
            totalCount >= MIN_PLAYERS_REQUIRED
        );
        messagingTemplate.convertAndSend("/topic/board/" + id, updateMessage);

        return ResponseEntity.ok(Map.of(
            "message", "Jugador salió de la partida exitosamente",
            "sessionId", id,
            "playerUuid", playerUuid,
            "remainingPlayers", totalCount
        ));
    }

    /**
     * Consulta el estado actual de un jugador específico en la sesión (Fallback REST sincronizado).
     */
    @GetMapping("/{id}/players/{playerUuid}")
    public ResponseEntity<Map<String, Object>> getPlayerStatus(
        @PathVariable("id") Long id,
        @PathVariable("playerUuid") String playerUuid
    ) {
        GameSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con ID: " + id));

        PlayerNode player = playerRepository.findByPlayerUuidOrPrefix(playerUuid)
            .stream()
            .filter(p -> p.getSession().getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en la sesión: " + playerUuid));

        return ResponseEntity.ok(Map.of(
            "playerUuid", player.getPlayerUuid(),
            "sessionId", id,
            "status", player.getStatus().name(),
            "infectedAt", player.getInfectedAt() != null ? player.getInfectedAt().toString() : "",
            "sessionStatus", session.getStatus().name()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "viral-simulation-backend",
            "minPlayersRequired", String.valueOf(MIN_PLAYERS_REQUIRED),
            "timestamp", Instant.now().toString()
        ));
    }
}
