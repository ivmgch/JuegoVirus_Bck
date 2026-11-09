package com.campus.viral.model.dto.response;

import com.campus.viral.model.domain.SessionStatus;

public record SessionResponse(
    Long sessionId,
    String sessionCode,
    SessionStatus status,
    int p0,
    double initialK,
    double initialA
) {}
