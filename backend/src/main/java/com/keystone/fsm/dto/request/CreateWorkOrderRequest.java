package com.keystone.fsm.dto.request;

import com.keystone.fsm.domain.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkOrderRequest(
    @NotBlank @Size(max = 150) String title,
    @Size(max = 4000) String description,
    @NotNull Priority priority,
    // Required for staff; customers are always scoped to their own organisation
    String customerId,
    @NotBlank String siteId,
    String assignedTechnicianId
) {}
