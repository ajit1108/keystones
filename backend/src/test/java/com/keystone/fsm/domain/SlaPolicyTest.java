package com.keystone.fsm.domain;

import com.keystone.fsm.domain.enums.Priority;
import com.keystone.fsm.domain.enums.SlaStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlaPolicyTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void slaWindowsMatchPriorityMatrix() {
        assertThat(SlaPolicy.hoursFor(Priority.CRITICAL)).isEqualTo(4);
        assertThat(SlaPolicy.hoursFor(Priority.HIGH)).isEqualTo(24);
        assertThat(SlaPolicy.hoursFor(Priority.MEDIUM)).isEqualTo(72);
        assertThat(SlaPolicy.hoursFor(Priority.LOW)).isEqualTo(168);
    }

    @Test
    void dueDateIsStartPlusWindow() {
        assertThat(SlaPolicy.dueAt(Priority.CRITICAL, START))
                .isEqualTo(START.plusSeconds(4 * 3600));
        assertThat(SlaPolicy.dueAt(Priority.LOW, START))
                .isEqualTo(START.plusSeconds(168 * 3600));
    }

    @Test
    void atRiskStartsAtEightyPercentElapsedAndBreachAtDueTime() {
        Instant due = SlaPolicy.dueAt(Priority.CRITICAL, START);
        long window = 4 * 3600;

        assertThat(SlaPolicy.statusFor(Priority.CRITICAL, due, START.plusSeconds(window / 2)))
                .isEqualTo(SlaStatus.ON_TRACK);
        assertThat(SlaPolicy.statusFor(Priority.CRITICAL, due,
                START.plusSeconds((long) (window * 0.8) - 1)))
                .isEqualTo(SlaStatus.ON_TRACK);
        assertThat(SlaPolicy.statusFor(Priority.CRITICAL, due,
                START.plusSeconds((long) (window * 0.8))))
                .isEqualTo(SlaStatus.AT_RISK);
        assertThat(SlaPolicy.statusFor(Priority.CRITICAL, due, due))
                .isEqualTo(SlaStatus.BREACHED);
        assertThat(SlaPolicy.statusFor(Priority.CRITICAL, due, due.plusSeconds(60)))
                .isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void missingDueDateIsTreatedAsOnTrack() {
        assertThat(SlaPolicy.statusFor(Priority.HIGH, null, START)).isEqualTo(SlaStatus.ON_TRACK);
    }
}
