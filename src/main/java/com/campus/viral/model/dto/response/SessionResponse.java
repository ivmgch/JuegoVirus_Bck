package com.campus.viral.model.dto.response;

import com.campus.viral.model.domain.SessionStatus;
import java.util.List;

public record SessionResponse(
    Long sessionId,
    String sessionCode,
    SessionStatus status,
    int p0,
    double initialK,
    double initialA,
    long totalPlayers,
    int minPlayersRequired,
    boolean canStart,
    List<String> playerUuids
) {
    public SessionResponse(Long sessionId, String sessionCode, SessionStatus status,
                           int p0, double initialK, double initialA) {
        this(sessionId, sessionCode, status, p0, initialK, initialA, 0, 3, false, List.of());
    }
}
