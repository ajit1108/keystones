package com.keystone.fsm.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssignTechnicianRequest(
    @NotBlank String technicianId
) {}
