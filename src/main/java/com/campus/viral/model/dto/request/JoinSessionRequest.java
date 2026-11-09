package com.campus.viral.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JoinSessionRequest(
    @NotBlank(message = "El hash MAC del dispositivo es obligatorio")
    String deviceMacHash,

    String fcmToken
) {}
