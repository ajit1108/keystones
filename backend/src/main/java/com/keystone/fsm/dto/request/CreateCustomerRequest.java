package com.keystone.fsm.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
    @NotBlank @Size(max = 120) String name,
    @Email @Size(max = 160) String contactEmail,
    @Size(max = 40) String contactPhone,
    @Size(max = 255) String address
) {}
