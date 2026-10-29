package com.keystone.fsm.dto.response;

import java.time.Instant;

public record SiteResponse(
    String id,
    String customerId,
    String customerName,
    String name,
    String address,
    String city,
    String state,
    String country,
    String postalCode,
    String contactName,
    String contactPhone,
    boolean active,
    Instant createdAt
) {}
