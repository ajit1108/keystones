package com.keystone.fsm.dto.response;

import com.keystone.fsm.domain.enums.Priority;
import com.keystone.fsm.domain.enums.SlaStatus;
import com.keystone.fsm.domain.enums.WorkOrderStatus;
import com.keystone.fsm.domain.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Boundary DTO for work orders. Entities are never serialised directly, and
 * line-item views drop internal columns such as who logged them.
 */
public record WorkOrderResponse(
    String id,
    String code,
    String title,
    String description,
    Priority priority,
    WorkOrderStatus status,
    SlaStatus slaStatus,
    String customerId,
    String customerName,
    String siteId,
    String siteName,
    String assignedTechnicianId,
    String assignedTechnicianName,
    Instant slaDueDate,
    Instant completedAt,
    Instant closedAt,
    List<StatusHistory> statusHistory,
    List<PartUsageView> partUsages,
    List<TimeLogView> timeLogs,
    List<AttachmentResponse> attachments,
    int totalMinutesLogged,
    BigDecimal partsCostTotal,
    Instant createdAt,
    Instant updatedAt
) {

    public record PartUsageView(
        String partId,
        String partNumber,
        String partName,
        int quantityUsed,
        BigDecimal unitCostAtTime,
        Instant addedAt,
        String notes
    ) {
        static PartUsageView from(PartUsage p) {
            return new PartUsageView(p.getPartId(), p.getPartNumber(), p.getPartName(),
                p.getQuantityUsed(), p.getUnitCostAtTime(), p.getAddedAt(), p.getNotes());
        }

        PartUsageView forCustomer() {
            return new PartUsageView(partId, partNumber, partName, quantityUsed, null, addedAt, null);
        }
    }

    public record TimeLogView(
        String technicianName,
        int minutesLogged,
        Instant loggedAt,
        String notes
    ) {
        static TimeLogView from(TimeLog t) {
            return new TimeLogView(t.getTechnicianName(), t.getMinutesLogged(), t.getLoggedAt(), t.getNotes());
        }
    }

    public static WorkOrderResponse from(WorkOrder wo) {
        return build(wo, false);
    }

    /** Strips internal commercial detail (costs, labour, internal notes) for customer self-service. */
    public static WorkOrderResponse forCustomer(WorkOrder wo) {
        return build(wo, true);
    }

    private static WorkOrderResponse build(WorkOrder wo, boolean customerView) {
        List<PartUsageView> parts = wo.getPartUsages().stream()
            .map(PartUsageView::from)
            .map(p -> customerView ? p.forCustomer() : p)
            .toList();

        List<TimeLogView> timeLogs = customerView
            ? List.of()
            : wo.getTimeLogs().stream().map(TimeLogView::from).toList();

        List<StatusHistory> history = customerView
            ? wo.getStatusHistory().stream().map(WorkOrderResponse::withoutInternalNote).toList()
            : wo.getStatusHistory();

        BigDecimal partsCost = customerView ? null : partCost(wo);
        int minutes = customerView ? 0 : wo.getTotalMinutesLogged();

        return new WorkOrderResponse(
            wo.getId(), wo.getCode(), wo.getTitle(), wo.getDescription(),
            wo.getPriority(), wo.getStatus(), wo.getSlaStatus(),
            wo.getCustomerId(), wo.getCustomerName(),
            wo.getSiteId(), wo.getSiteName(),
            wo.getAssignedTechnicianId(), wo.getAssignedTechnicianName(),
            wo.getSlaDueDate(), wo.getCompletedAt(), wo.getClosedAt(),
            history, parts, timeLogs,
            wo.getAttachments().stream().map(AttachmentResponse::from).toList(),
            minutes, partsCost,
            wo.getCreatedAt(), wo.getUpdatedAt());
    }

    private static StatusHistory withoutInternalNote(StatusHistory h) {
        return StatusHistory.builder()
            .previousStatus(h.getPreviousStatus())
            .newStatus(h.getNewStatus())
            .changedByUserName(h.getChangedByUserName())
            .changedAt(h.getChangedAt())
            .build();
    }

    private static BigDecimal partCost(WorkOrder wo) {
        return wo.getPartUsages().stream()
            .map(p -> p.getUnitCostAtTime() == null
                ? BigDecimal.ZERO
                : p.getUnitCostAtTime().multiply(BigDecimal.valueOf(p.getQuantityUsed())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
