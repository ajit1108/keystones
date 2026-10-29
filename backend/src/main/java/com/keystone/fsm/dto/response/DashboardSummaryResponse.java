package com.keystone.fsm.dto.response;

import java.util.Map;

public record DashboardSummaryResponse(
    long totalWorkOrders,
    Map<String, Long> byStatus,
    long overdueCount,
    long slaBreachedCount,
    long slaAtRiskCount,
    long slaOnTrackCount,
    double slaCompliancePercent,
    Map<String, Long> byPriority,
    Map<String, Long> byTechnician,
    Map<String, Long> bySite
) {}
