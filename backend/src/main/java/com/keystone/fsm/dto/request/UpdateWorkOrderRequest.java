package com.keystone.fsm.dto.request;

import com.keystone.fsm.domain.enums.Priority;
import jakarta.validation.constraints.Size;

public record UpdateWorkOrderRequest(
    @Size(min = 1, max = 150) String title,
    @Size(max = 4000) String description,
    Priority priority,
    String siteId
) {}
