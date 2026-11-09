package com.campus.viral.model.entity;

import com.campus.viral.model.domain.SessionStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "game_sessions")
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, unique = true, length = 64)
    private String sessionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status = SessionStatus.WAITING_FOR_PLAYERS;

    @Column(name = "initial_infected_p0", nullable = false)
    private int initialInfectedP0;

    @Column(name = "carrying_capacity_k", nullable = false)
    private double carryingCapacityK;

    @Column(name = "integration_constant_a", nullable = false)
    private double integrationConstantA;

    @Column(name = "growth_rate_r", nullable = false)
    private double growthRateR = 0.05;

    @Column(name = "inflection_alert_sent", nullable = false)
    private boolean inflectionAlertSent = false;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public GameSession() {
    }

    public GameSession(String sessionCode, SessionStatus status, int initialInfectedP0,
                       double carryingCapacityK, double integrationConstantA, Instant startedAt) {
        this.sessionCode = sessionCode;
        this.status = status;
        this.initialInfectedP0 = initialInfectedP0;
        this.carryingCapacityK = carryingCapacityK;
        this.integrationConstantA = integrationConstantA;
        this.startedAt = startedAt;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public int getInitialInfectedP0() {
        return initialInfectedP0;
    }

    public void setInitialInfectedP0(int initialInfectedP0) {
        this.initialInfectedP0 = initialInfectedP0;
    }

    public double getCarryingCapacityK() {
        return carryingCapacityK;
    }

    public void setCarryingCapacityK(double carryingCapacityK) {
        this.carryingCapacityK = carryingCapacityK;
    }

    public double getIntegrationConstantA() {
        return integrationConstantA;
    }

    public void setIntegrationConstantA(double integrationConstantA) {
        this.integrationConstantA = integrationConstantA;
    }

    public double getGrowthRateR() {
        return growthRateR;
    }

    public void setGrowthRateR(double growthRateR) {
        this.growthRateR = growthRateR;
    }

    public boolean isInflectionAlertSent() {
        return inflectionAlertSent;
    }

    public void setInflectionAlertSent(boolean inflectionAlertSent) {
        this.inflectionAlertSent = inflectionAlertSent;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
