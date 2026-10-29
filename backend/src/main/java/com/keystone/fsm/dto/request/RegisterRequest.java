package com.keystone.fsm.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegisterRequest(
    @NotBlank @Size(max = 160) String fullName,
    @NotBlank @Email @Size(max = 160) String email,
    @NotBlank @Size(min = 8, max = 100)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
             message = "Password must be at least 8 characters and include a letter and a number")
    String password,
    @NotBlank @Size(max = 100) String confirmPassword
) {
    public RegisterRequest {
        if (fullName != null) fullName = fullName.trim().replaceAll("\\s+", " ");
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }
}
