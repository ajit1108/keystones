package com.keystone.fsm.dto.request;

import com.keystone.fsm.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank @Email @Size(max = 160) String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    @NotNull Role role,
    String customerId  // required when role == CUSTOMER
) {}
