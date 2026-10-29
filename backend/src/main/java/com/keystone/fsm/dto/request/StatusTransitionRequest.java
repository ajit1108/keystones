package com.keystone.fsm.dto.request;

import com.keystone.fsm.domain.enums.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusTransitionRequest(
    @NotNull WorkOrderStatus newStatus,
    @Size(max = 500) String note
) {}
