package com.campus.viral.model.dto.response;

public record BoardUpdateMessage(
    Long sessionId,
    double k,
    double a,
    double r,
    long totalInfected,
    long totalHealthy,
    double theoreticalInfectedPt,
    long timestampMs
) {}
