package com.keystone.fsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddPartUsageRequest(
    @NotBlank String partId,
    @NotNull @Positive Integer quantityUsed,
    @Size(max = 500) String notes
) {}
