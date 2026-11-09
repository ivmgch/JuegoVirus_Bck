package com.campus.viral.model.dto.response;

import com.campus.viral.model.domain.PlayerStatus;
import java.time.Instant;

public record JoinResponse(
    String playerUuid,
    Long sessionId,
    PlayerStatus assignedStatus,
    Instant joinedAt
) {}
