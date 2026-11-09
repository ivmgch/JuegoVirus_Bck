package com.campus.viral.model.dto.response;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    int status,
    String error,
    String message,
    List<String> details,
    Instant timestamp
) {
    public ApiErrorResponse(int status, String error, String message, List<String> details) {
        this(status, error, message, details, Instant.now());
    }

    public ApiErrorResponse(int status, String error, String message) {
        this(status, error, message, List.of(), Instant.now());
    }
}
