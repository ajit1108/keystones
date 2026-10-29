package com.keystone.fsm.domain.model;

import com.keystone.fsm.domain.enums.Priority;
import com.keystone.fsm.domain.enums.SlaStatus;
import com.keystone.fsm.domain.enums.WorkOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Central document of the KEYSTONE domain.
 * Embeds StatusHistory, PartUsage and TimeLog to keep related data co-located.
 * Customer, Site and Technician are referenced by their ids.
 */
@Document(collection = "work_orders")
@CompoundIndexes({
    @CompoundIndex(name = "wo_customer_status_idx", def = "{'customerId': 1, 'status': 1}"),
    @CompoundIndex(name = "wo_technician_status_idx", def = "{'assignedTechnicianId': 1, 'status': 1}"),
    @CompoundIndex(name = "wo_site_idx", def = "{'siteId': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrder {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;          // Human-readable e.g. WO-20241028-0001

    private String title;
    private String description;

    private Priority priority;
    private WorkOrderStatus status;
    private SlaStatus slaStatus;

    // References
    private String customerId;
    private String customerName;      // Denormalised for display performance
    private String siteId;
    private String siteName;          // Denormalised
    private String assignedTechnicianId;
    private String assignedTechnicianName; // Denormalised

    private Instant slaDueDate;
    private Instant completedAt;
    private Instant closedAt;

    // Embedded collections
    @Builder.Default
    private List<StatusHistory> statusHistory = new ArrayList<>();

    @Builder.Default
    private List<PartUsage> partUsages = new ArrayList<>();

    @Builder.Default
    private List<TimeLog> timeLogs = new ArrayList<>();

    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Computed helper – total minutes across all time logs */
    public int getTotalMinutesLogged() {
        if (timeLogs == null) return 0;
        return timeLogs.stream().mapToInt(TimeLog::getMinutesLogged).sum();
    }
}
