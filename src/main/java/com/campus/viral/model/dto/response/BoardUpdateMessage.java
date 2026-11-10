package com.campus.viral.model.dto.response;

public record BoardUpdateMessage(
    Long sessionId,
    double k,
    double a,
    double r,
    long totalInfected,
    long totalHealthy,
    double theoreticalInfectedPt,
    long timestampMs,
    String sessionStatus,
    long totalPlayers,
    int minPlayersRequired,
    boolean canStart
) {
    public BoardUpdateMessage(
        Long sessionId,
        double k,
        double a,
        double r,
        long totalInfected,
        long totalHealthy,
        double theoreticalInfectedPt,
        long timestampMs
    ) {
        this(
            sessionId,
            k,
            a,
            r,
            totalInfected,
            totalHealthy,
            theoreticalInfectedPt,
            timestampMs,
            "ACTIVE",
            totalInfected + totalHealthy,
            3,
            (totalInfected + totalHealthy) >= 3
        );
    }
}
