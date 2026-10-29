package com.keystone.fsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePartRequest(
    @NotBlank @Size(max = 40) String partNumber,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 500) String description,
    @Size(max = 20) String unitOfMeasure,
    @PositiveOrZero int quantityInStock,
    @PositiveOrZero int reorderLevel,
    @PositiveOrZero BigDecimal unitCost
) {}
