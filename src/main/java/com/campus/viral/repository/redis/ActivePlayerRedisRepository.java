package com.campus.viral.repository.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Repository
public class ActivePlayerRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(ActivePlayerRedisRepository.class);

    private static final String ACTIVE_PLAYERS_KEY = "game:session:%d:active_players";
    private static final String BLE_DENSITY_KEY = "game:session:%d:ble_density";

    private final StringRedisTemplate redisTemplate;
    private final long heartbeatWindowMs;
    private final long densityWindowMs;

    // Respaldo en memoria local para desarrollo rápido sin Redis
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Long>> inMemoryActivePlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<DensityRecord>> inMemoryBleDensity = new ConcurrentHashMap<>();
    private final AtomicBoolean redisWarningLogged = new AtomicBoolean(false);

    private record DensityRecord(String playerUuid, long timestampMs, int count) {}

    public ActivePlayerRedisRepository(
        StringRedisTemplate redisTemplate,
        @Value("${app.simulation.heartbeat-window-ms:120000}") long heartbeatWindowMs,
        @Value("${app.simulation.density-window-ms:60000}") long densityWindowMs
    ) {
        this.redisTemplate = redisTemplate;
        this.heartbeatWindowMs = heartbeatWindowMs;
        this.densityWindowMs = densityWindowMs;
    }

    /**
     * Registra o renueva el Heartbeat de un jugador mediante ZADD en Redis,
     * con fallback automático en memoria si Redis no está activo.
     */
    public void recordHeartbeat(Long sessionId, String playerUuid, int bleNearbyCount) {
        long now = Instant.now().toEpochMilli();
        try {
            String sessionKey = String.format(ACTIVE_PLAYERS_KEY, sessionId);
            String densityKey = String.format(BLE_DENSITY_KEY, sessionId);

            redisTemplate.opsForZSet().add(sessionKey, playerUuid, (double) now);

            if (bleNearbyCount > 0) {
                String densityEvent = String.format("%s:%d:%d", playerUuid, now, bleNearbyCount);
                redisTemplate.opsForZSet().add(densityKey, densityEvent, (double) now);
            }
        } catch (Exception e) {
            logRedisFallbackOnce(e);
            // Fallback en memoria local
            inMemoryActivePlayers.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(playerUuid, now);
            if (bleNearbyCount > 0) {
                inMemoryBleDensity.computeIfAbsent(sessionId, k -> new ConcurrentLinkedQueue<>())
                    .add(new DensityRecord(playerUuid, now, bleNearbyCount));
            }
        }
    }

    /**
     * Limpia usuarios desconectados (> 2 min) y retorna k (Capacidad de Carga)
     */
    public double getActiveCarryingCapacityK(Long sessionId) {
        long now = Instant.now().toEpochMilli();
        double threshold = (double) (now - heartbeatWindowMs);

        try {
            String sessionKey = String.format(ACTIVE_PLAYERS_KEY, sessionId);
            redisTemplate.opsForZSet().removeRangeByScore(sessionKey, 0.0, threshold);
            Long activeCount = redisTemplate.opsForZSet().zCard(sessionKey);
            return activeCount != null ? activeCount.doubleValue() : 0.0;
        } catch (Exception e) {
            logRedisFallbackOnce(e);
            // Fallback en memoria local
            ConcurrentHashMap<String, Long> players = inMemoryActivePlayers.get(sessionId);
            if (players == null || players.isEmpty()) {
                return 0.0;
            }
            long staleThreshold = now - heartbeatWindowMs;
            players.entrySet().removeIf(entry -> entry.getValue() < staleThreshold);
            return (double) players.size();
        }
    }

    /**
     * Limpia y cuenta las interacciones BLE reportadas en la ventana reciente
     */
    public long getRecentBleInteractionsCount(Long sessionId) {
        long now = Instant.now().toEpochMilli();
        double threshold = (double) (now - densityWindowMs);

        try {
            String densityKey = String.format(BLE_DENSITY_KEY, sessionId);
            redisTemplate.opsForZSet().removeRangeByScore(densityKey, 0.0, threshold);
            Long count = redisTemplate.opsForZSet().zCard(densityKey);
            return count != null ? count : 0L;
        } catch (Exception e) {
            logRedisFallbackOnce(e);
            // Fallback en memoria local
            ConcurrentLinkedQueue<DensityRecord> queue = inMemoryBleDensity.get(sessionId);
            if (queue == null || queue.isEmpty()) {
                return 0L;
            }
            long staleThreshold = now - densityWindowMs;
            queue.removeIf(record -> record.timestampMs() < staleThreshold);
            return queue.size();
        }
    }

    public void removePlayer(Long sessionId, String playerUuid) {
        try {
            String sessionKey = String.format(ACTIVE_PLAYERS_KEY, sessionId);
            redisTemplate.opsForZSet().remove(sessionKey, playerUuid);
        } catch (Exception e) {
            ConcurrentHashMap<String, Long> players = inMemoryActivePlayers.get(sessionId);
            if (players != null) {
                players.remove(playerUuid);
            }
        }
    }

    private void logRedisFallbackOnce(Exception e) {
        if (redisWarningLogged.compareAndSet(false, true)) {
            log.warn("⚠️ Servidor Redis no disponible en localhost:6379 ({}: {}). Activando motor de Heartbeats en memoria local.",
                e.getClass().getSimpleName(), e.getMessage());
            log.info("ℹ️ Para utilizar Redis 7 en producción o pruebas de alta carga, ejecuta: 'docker compose up -d'.");
        }
    }
}
