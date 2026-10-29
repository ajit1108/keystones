package com.keystone.fsm.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Embedded sub-document recording a part used on a work order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartUsage {

    private String partId;
    private String partNumber;
    private String partName;
    private int quantityUsed;
    private BigDecimal unitCostAtTime;
    private String addedByUserId;
    private Instant addedAt;
    private String notes;
}
