package com.keystone.fsm.dto.response;

import com.keystone.fsm.domain.enums.Role;

public record AuthResponse(
    String token,
    String tokenType,
    String id,
    String email,
    String firstName,
    String lastName,
    Role role,
    String customerId
) {
    public static AuthResponse of(String token, com.keystone.fsm.domain.model.User user) {
        return new AuthResponse(
            token, "Bearer",
            user.getId(), user.getEmail(),
            user.getFirstName(), user.getLastName(),
            user.getRole(), user.getCustomerId()
        );
    }
}
