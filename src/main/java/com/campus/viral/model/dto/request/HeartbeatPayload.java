package com.campus.viral.model.dto.request;

public record HeartbeatPayload(
    Long sessionId,
    String playerUuid,
    int bleNearbyCount
) {}
