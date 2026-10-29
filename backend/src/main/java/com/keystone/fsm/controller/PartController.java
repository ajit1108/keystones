package com.keystone.fsm.controller;

import com.keystone.fsm.dto.request.CreatePartRequest;
import com.keystone.fsm.dto.response.PartResponse;
import com.keystone.fsm.service.PartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parts")
@RequiredArgsConstructor
@Tag(name = "Parts", description = "Inventory parts management")
@SecurityRequirement(name = "bearerAuth")
public class PartController {

    private final PartService partService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new inventory part")
    public ResponseEntity<PartResponse> createPart(@Valid @RequestBody CreatePartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partService.createPart(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER', 'TECHNICIAN')")
    @Operation(summary = "List parts with optional search and pagination")
    public ResponseEntity<Page<PartResponse>> getParts(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(partService.getParts(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER', 'TECHNICIAN')")
    @Operation(summary = "Get a part by id")
    public ResponseEntity<PartResponse> getPartById(@PathVariable String id) {
        return ResponseEntity.ok(partService.getPartById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a part")
    public ResponseEntity<PartResponse> updatePart(
            @PathVariable String id,
            @Valid @RequestBody CreatePartRequest request) {
        return ResponseEntity.ok(partService.updatePart(id, request));
    }
}
