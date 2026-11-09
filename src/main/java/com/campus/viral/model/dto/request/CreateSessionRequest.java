package com.campus.viral.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
    @NotBlank(message = "El código de sesión es obligatorio")
    String sessionCode,

    @Min(value = 1, message = "Debe haber al menos 1 paciente cero P(0)")
    int initialP0
) {}
