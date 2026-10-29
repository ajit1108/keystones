package com.keystone.fsm.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {
    public LoginRequest {
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }
}
