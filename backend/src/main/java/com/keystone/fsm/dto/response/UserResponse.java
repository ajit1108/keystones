package com.keystone.fsm.dto.response;

import com.keystone.fsm.domain.enums.Role;
import java.time.Instant;

public record UserResponse(
    String id,
    String email,
    String firstName,
    String lastName,
    String fullName,
    Role role,
    String customerId,
    boolean enabled,
    Instant createdAt
) {}
