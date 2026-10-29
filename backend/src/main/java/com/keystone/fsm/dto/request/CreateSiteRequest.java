package com.keystone.fsm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSiteRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 255) String address,
    @Size(max = 80) String city,
    @Size(max = 80) String state,
    @Size(max = 80) String country,
    @Size(max = 20) String postalCode,
    @Size(max = 120) String contactName,
    @Size(max = 40) String contactPhone
) {}
