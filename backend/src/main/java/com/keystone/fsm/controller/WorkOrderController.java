package com.keystone.fsm.controller;

import com.keystone.fsm.domain.enums.Priority;
import com.keystone.fsm.domain.enums.WorkOrderStatus;
import com.keystone.fsm.dto.request.*;
import com.keystone.fsm.dto.response.WorkOrderResponse;
import com.keystone.fsm.security.KeystonePrincipal;
import com.keystone.fsm.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
@Tag(name = "Work Orders", description = "Work order lifecycle management")
@SecurityRequirement(name = "bearerAuth")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @PostMapping
    @Operation(summary = "Create a new work order / customer request")
    public ResponseEntity<WorkOrderResponse> create(
            @Valid @RequestBody CreateWorkOrderRequest request,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workOrderService.createWorkOrder(request, actor));
    }

    @GetMapping
    @Operation(summary = "List work orders with optional filters, search, and pagination (role-scoped)")
    public ResponseEntity<Page<WorkOrderResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String technicianId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(
                workOrderService.getWorkOrders(search, status, priority, customerId, technicianId, actor, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a work order by id, including history, parts, time and photos")
    public ResponseEntity<WorkOrderResponse> getById(
            @PathVariable String id,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(workOrderService.getWorkOrderById(id, actor));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an open work order (dispatcher/manager)")
    public ResponseEntity<WorkOrderResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateWorkOrderRequest request,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(workOrderService.updateWorkOrder(id, request, actor));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign or reassign a technician (dispatcher/manager)")
    public ResponseEntity<WorkOrderResponse> assign(
            @PathVariable String id,
            @Valid @RequestBody AssignTechnicianRequest request,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(workOrderService.assignTechnician(id, request, actor));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Transition status; 409 when the transition is illegal")
    public ResponseEntity<WorkOrderResponse> transition(
            @PathVariable String id,
            @Valid @RequestBody StatusTransitionRequest request,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(workOrderService.transitionStatus(id, request, actor));
    }

    @PostMapping("/{id}/parts")
    @Operation(summary = "Record part usage (decrements inventory atomically)")
    public ResponseEntity<WorkOrderResponse> addPart(
            @PathVariable String id,
            @Valid @RequestBody AddPartUsageRequest request,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(workOrderService.addPartUsage(id, request, actor));
    }

    @PostMapping("/{id}/time")
    @Operation(summary = "Log time on a work order")
    public ResponseEntity<WorkOrderResponse> logTime(
            @PathVariable String id,
            @Valid @RequestBody LogTimeRequest request,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(workOrderService.logTime(id, request, actor));
    }

    @PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a photo against a work order")
    public ResponseEntity<WorkOrderResponse> uploadAttachment(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workOrderService.addAttachment(id, file, actor));
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    @Operation(summary = "Download an attachment (role-scoped)")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable String id,
            @PathVariable String attachmentId,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        var file = workOrderService.getAttachment(id, attachmentId, actor);
        MediaType mediaType = MediaType.parseMediaType(file.attachment().getContentType());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.attachment().getFileName())
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.resource());
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @Operation(summary = "Delete an attachment (manager or uploader)")
    public ResponseEntity<WorkOrderResponse> deleteAttachment(
            @PathVariable String id,
            @PathVariable String attachmentId,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(workOrderService.deleteAttachment(id, attachmentId, actor));
    }
}
