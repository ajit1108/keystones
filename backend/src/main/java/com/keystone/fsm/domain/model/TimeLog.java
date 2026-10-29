package com.keystone.fsm.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Embedded sub-document recording labour time logged against a work order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeLog {

    private String technicianId;
    private String technicianName;
    private int minutesLogged;
    private Instant loggedAt;
    private String notes;
}
