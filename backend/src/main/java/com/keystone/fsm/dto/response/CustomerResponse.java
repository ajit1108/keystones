package com.keystone.fsm.dto.response;

import java.time.Instant;

public record CustomerResponse(
    String id,
    String name,
    String contactEmail,
    String contactPhone,
    String address,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
