package com.campus.viral.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "infection_audit_logs",
    indexes = {
        @Index(name = "idx_audit_session", columnList = "session_id"),
        @Index(name = "idx_audit_timestamp", columnList = "created_at")
    }
)
public class InfectionAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Column(name = "infected_player_uuid", nullable = false, length = 36)
    private String infectedPlayerUuid;

    @Column(name = "transmitter_beacon_uuid", length = 36)
    private String transmitterBeaconUuid;

    @Column(name = "exposure_duration_seconds")
    private int exposureDurationSeconds;

    @Column(name = "estimated_distance_meters")
    private double estimatedDistanceMeters;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public InfectionAuditLog() {
    }

    public InfectionAuditLog(GameSession session, String infectedPlayerUuid, String transmitterBeaconUuid,
                             int exposureDurationSeconds, double estimatedDistanceMeters) {
        this.session = session;
        this.infectedPlayerUuid = infectedPlayerUuid;
        this.transmitterBeaconUuid = transmitterBeaconUuid;
        this.exposureDurationSeconds = exposureDurationSeconds;
        this.estimatedDistanceMeters = estimatedDistanceMeters;
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

    public String getInfectedPlayerUuid() {
        return infectedPlayerUuid;
    }

    public void setInfectedPlayerUuid(String infectedPlayerUuid) {
        this.infectedPlayerUuid = infectedPlayerUuid;
    }

    public String getTransmitterBeaconUuid() {
        return transmitterBeaconUuid;
    }

    public void setTransmitterBeaconUuid(String transmitterBeaconUuid) {
        this.transmitterBeaconUuid = transmitterBeaconUuid;
    }

    public int getExposureDurationSeconds() {
        return exposureDurationSeconds;
    }

    public void setExposureDurationSeconds(int exposureDurationSeconds) {
        this.exposureDurationSeconds = exposureDurationSeconds;
    }

    public double getEstimatedDistanceMeters() {
        return estimatedDistanceMeters;
    }

    public void setEstimatedDistanceMeters(double estimatedDistanceMeters) {
        this.estimatedDistanceMeters = estimatedDistanceMeters;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
