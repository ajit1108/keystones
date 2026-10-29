package com.keystone.fsm.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Atomic per-day counter backing human-readable work-order codes.
 */
@Document(collection = "sequences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailySequence {

    /** yyyyMMdd (UTC) */
    @Id
    private String day;

    private long seq;
}
