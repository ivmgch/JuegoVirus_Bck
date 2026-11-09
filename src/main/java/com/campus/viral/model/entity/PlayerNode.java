package com.campus.viral.model.entity;

import com.campus.viral.model.domain.PlayerStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "player_nodes",
    indexes = {
        @Index(name = "idx_player_session_status", columnList = "session_id, status"),
        @Index(name = "idx_player_device_hash", columnList = "device_mac_hash")
    }
)
public class PlayerNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Column(name = "device_mac_hash", nullable = false, length = 64)
    private String deviceMacHash;

    @Column(name = "player_uuid", nullable = false, unique = true, length = 36)
    private String playerUuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerStatus status = PlayerStatus.HEALTHY;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "infected_at")
    private Instant infectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PlayerNode() {
    }

    public PlayerNode(GameSession session, String deviceMacHash, String playerUuid,
                      PlayerStatus status, String fcmToken, Instant infectedAt) {
        this.session = session;
        this.deviceMacHash = deviceMacHash;
        this.playerUuid = playerUuid;
        this.status = status;
        this.fcmToken = fcmToken;
        this.infectedAt = infectedAt;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GameSession getSession() {
        return session;
    }

    public void setSession(GameSession session) {
        this.session = session;
    }

    public String getDeviceMacHash() {
        return deviceMacHash;
    }

    public void setDeviceMacHash(String deviceMacHash) {
        this.deviceMacHash = deviceMacHash;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public PlayerStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public Instant getInfectedAt() {
        return infectedAt;
    }

    public void setInfectedAt(Instant infectedAt) {
        this.infectedAt = infectedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
