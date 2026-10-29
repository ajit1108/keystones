package com.keystone.fsm.domain;

import com.keystone.fsm.domain.enums.Priority;
import com.keystone.fsm.domain.enums.SlaStatus;
import com.keystone.fsm.domain.model.WorkOrder;

import java.time.Duration;
import java.time.Instant;

/**
 * Single source of truth for SLA response windows per priority.
 * A job is AT RISK once 80% of its response window has elapsed.
 */
public final class SlaPolicy {

    private static final double AT_RISK_FRACTION = 0.8;

    private SlaPolicy() {}

    public static int hoursFor(Priority priority) {
        return switch (priority) {
            case CRITICAL -> 4;
            case HIGH -> 24;
            case MEDIUM -> 72;
            case LOW -> 168;
        };
    }

    public static Instant dueAt(Priority priority, Instant from) {
        return from.plusSeconds(hoursFor(priority) * 3600L);
    }

    public static SlaStatus statusFor(Priority priority, Instant dueAt, Instant now) {
        if (dueAt == null) {
            return SlaStatus.ON_TRACK;
        }
        if (!now.isBefore(dueAt)) {
            return SlaStatus.BREACHED;
        }
        long windowSeconds = hoursFor(priority) * 3600L;
        long remaining = Duration.between(now, dueAt).getSeconds();
        double elapsedFraction = 1.0 - ((double) remaining / windowSeconds);
        return elapsedFraction >= AT_RISK_FRACTION ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK;
    }

    public static SlaStatus statusFor(WorkOrder wo, Instant now) {
        return statusFor(wo.getPriority(), wo.getSlaDueDate(), now);
    }
}
