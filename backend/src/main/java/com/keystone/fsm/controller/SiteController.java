package com.keystone.fsm.controller;

import com.keystone.fsm.dto.request.CreateSiteRequest;
import com.keystone.fsm.dto.response.SiteResponse;
import com.keystone.fsm.security.KeystonePrincipal;
import com.keystone.fsm.service.SiteService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/sites")
@RequiredArgsConstructor
@Tag(name = "Sites", description = "Site management per customer")
@SecurityRequirement(name = "bearerAuth")
public class SiteController {

    private final SiteService siteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER')")
    @Operation(summary = "Create a site under a customer")
    public ResponseEntity<SiteResponse> createSite(
            @PathVariable String customerId,
            @Valid @RequestBody CreateSiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(siteService.createSite(customerId, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER', 'CUSTOMER')")
    @Operation(summary = "List sites for a customer (customers may only read their own)")
    public ResponseEntity<Page<SiteResponse>> getSites(
            @PathVariable String customerId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(siteService.getSitesByCustomer(customerId, search, pageable, actor));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER', 'CUSTOMER')")
    @Operation(summary = "List all sites for a customer (for selectors)")
    public ResponseEntity<List<SiteResponse>> getAllSites(
            @PathVariable String customerId,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(siteService.getAllSitesByCustomer(customerId, actor));
    }

    @GetMapping("/{siteId}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER', 'CUSTOMER')")
    @Operation(summary = "Get a site by id")
    public ResponseEntity<SiteResponse> getSiteById(
            @PathVariable String customerId,
            @PathVariable String siteId,
            @AuthenticationPrincipal KeystonePrincipal actor) {
        return ResponseEntity.ok(siteService.getSiteById(customerId, siteId, actor));
    }

    @PutMapping("/{siteId}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER')")
    @Operation(summary = "Edit a site")
    public ResponseEntity<SiteResponse> updateSite(
            @PathVariable String customerId,
            @PathVariable String siteId,
            @Valid @RequestBody CreateSiteRequest request) {
        return ResponseEntity.ok(siteService.updateSite(customerId, siteId, request));
    }
}
