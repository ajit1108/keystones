package com.keystone.fsm.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PartResponse(
    String id,
    String partNumber,
    String name,
    String description,
    String unitOfMeasure,
    int quantityInStock,
    int reorderLevel,
    BigDecimal unitCost,
    Instant createdAt,
    Instant updatedAt
) {}
