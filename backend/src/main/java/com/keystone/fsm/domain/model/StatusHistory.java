package com.keystone.fsm.domain.model;

import com.keystone.fsm.domain.enums.WorkOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Embedded sub-document inside a WorkOrder capturing every status transition.
 * Append-only – never updated or deleted after creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistory {

    private WorkOrderStatus previousStatus;
    private WorkOrderStatus newStatus;
    /** Id of the user who performed the transition */
    private String changedByUserId;
    private String changedByUserName;
    private Instant changedAt;
    private String note;
}
