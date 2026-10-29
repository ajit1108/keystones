package com.keystone.fsm.controller;

import com.keystone.fsm.domain.enums.WorkOrderStatus;
import com.keystone.fsm.dto.response.DashboardSummaryResponse;
import com.keystone.fsm.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Dashboard and reporting endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "Dashboard metrics, optionally filtered by customer, technician, status or date range")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String technicianId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until) {
        return ResponseEntity.ok(reportService.getSummary(customerId, technicianId, status, from, until));
    }
}
