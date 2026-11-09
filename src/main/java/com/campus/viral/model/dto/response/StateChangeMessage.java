package com.campus.viral.model.dto.response;

public record StateChangeMessage(
    String playerUuid,
    String newStatus,
    String infectedByBeaconUuid,
    String hapticFeedbackPattern,
    String timestamp
) {
    public StateChangeMessage(String playerUuid, String newStatus, String infectedByBeaconUuid, String timestamp) {
        this(playerUuid, newStatus, infectedByBeaconUuid, "HEAVY_IMPACT", timestamp);
    }
}
