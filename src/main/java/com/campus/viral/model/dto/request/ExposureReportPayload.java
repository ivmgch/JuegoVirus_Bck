package com.campus.viral.model.dto.request;

public record ExposureReportPayload(
    Long sessionId,
    String healthyPlayerUuid,
    String infectedBeaconUuid,
    int exposureDurationSeconds,
    double estimatedDistanceMeters
) {}
