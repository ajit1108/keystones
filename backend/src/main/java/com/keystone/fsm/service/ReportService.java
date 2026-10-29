package com.keystone.fsm.service;

import com.keystone.fsm.domain.enums.Priority;
import com.keystone.fsm.domain.enums.SlaStatus;
import com.keystone.fsm.domain.enums.WorkOrderStatus;
import com.keystone.fsm.domain.model.WorkOrder;
import com.keystone.fsm.dto.response.DashboardSummaryResponse;
import com.keystone.fsm.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operational metrics for the manager dashboard. All figures honour the same
 * optional filters so what the manager sees matches the work order list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Set<WorkOrderStatus> DONE = Set.of(
            WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED, WorkOrderStatus.COMPLETED);

    private final MongoTemplate mongoTemplate;

    public DashboardSummaryResponse getSummary(String customerId, String technicianId,
                                               WorkOrderStatus status, Instant from, Instant until) {
        if (from != null && until != null && from.isAfter(until)) {
            throw new BusinessRuleException("'from' must be before 'until'");
        }

        List<Criteria> filters = filters(customerId, technicianId, status, from, until);
        long total = count(filters);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (WorkOrderStatus s : WorkOrderStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        overlay(byStatus, groupCount(filters, "status"));

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            byPriority.put(p.name(), 0L);
        }
        overlay(byPriority, groupCount(filters, "priority"));

        Map<String, Long> byTechnician = groupCount(filters, "assignedTechnicianName");
        replaceNullKey(byTechnician, "Unassigned");

        Map<String, Long> bySite = groupCount(filters, "siteName");
        replaceNullKey(bySite, "Unknown site");

        long breached = count(with(filters, Criteria.where("slaStatus").is(SlaStatus.BREACHED)));
        long atRisk = count(with(filters, Criteria.where("slaStatus").is(SlaStatus.AT_RISK)));
        long onTrack = count(with(filters, Criteria.where("slaStatus").is(SlaStatus.ON_TRACK)));

        long cancelled = byStatus.getOrDefault(WorkOrderStatus.CANCELLED.name(), 0L);
        long evaluated = total - cancelled;
        double compliance = evaluated <= 0
                ? 100.0
                : Math.max(0.0, Math.min(100.0, ((double) (evaluated - breached) / evaluated) * 100.0));

        long overdue = count(with(filters,
                Criteria.where("slaDueDate").lt(Instant.now())
                        .and("status").nin(DONE)));

        return new DashboardSummaryResponse(
                total, byStatus, overdue,
                breached, atRisk, onTrack,
                Math.round(compliance * 10.0) / 10.0,
                byPriority, byTechnician, bySite);
    }

    private List<Criteria> filters(String customerId, String technicianId,
                                   WorkOrderStatus status, Instant from, Instant until) {
        List<Criteria> filters = new ArrayList<>();
        if (customerId != null && !customerId.isBlank()) {
            filters.add(Criteria.where("customerId").is(customerId));
        }
        if (technicianId != null && !technicianId.isBlank()) {
            filters.add(Criteria.where("assignedTechnicianId").is(technicianId));
        }
        if (status != null) {
            filters.add(Criteria.where("status").is(status));
        }
        if (from != null) {
            filters.add(Criteria.where("createdAt").gte(from));
        }
        if (until != null) {
            filters.add(Criteria.where("createdAt").lte(until));
        }
        return filters;
    }

    private List<Criteria> with(List<Criteria> base, Criteria extra) {
        List<Criteria> list = new ArrayList<>(base);
        list.add(extra);
        return list;
    }

    private Criteria all(List<Criteria> filters) {
        return filters.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(filters.toArray(Criteria[]::new));
    }

    private long count(List<Criteria> filters) {
        return mongoTemplate.count(new Query(all(filters)), WorkOrder.class);
    }

    private Map<String, Long> groupCount(List<Criteria> filters, String field) {
        GroupOperation group = Aggregation.group(field).count().as("count");
        Aggregation aggregation = filters.isEmpty()
                ? Aggregation.newAggregation(group)
                : Aggregation.newAggregation(Aggregation.match(all(filters)), group);

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, "work_orders", Document.class);

        Map<String, Long> counts = new LinkedHashMap<>();
        results.getMappedResults().forEach(doc -> {
            Object id = doc.get("_id");
            String key = id == null ? null : String.valueOf(id);
            long value = ((Number) doc.get("count")).longValue();
            counts.merge(key, value, Long::sum);
        });

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed());
        Map<String, Long> ordered = new LinkedHashMap<>();
        sorted.forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
    }

    private void replaceNullKey(Map<String, Long> map, String replacement) {
        if (map.containsKey(null)) {
            Long value = map.remove(null);
            map.put(replacement, value);
        }
    }

    private void overlay(Map<String, Long> target, Map<String, Long> counts) {
        counts.forEach((k, v) -> {
            if (k != null) {
                target.put(k, v);
            }
        });
    }
}
