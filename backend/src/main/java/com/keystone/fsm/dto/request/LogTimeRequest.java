package com.keystone.fsm.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LogTimeRequest(
    @NotNull @Min(1) @Max(1440) Integer minutesLogged,
    @Size(max = 500) String notes
) {}
