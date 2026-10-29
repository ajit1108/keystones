package com.keystone.fsm.controller;

import com.keystone.fsm.dto.request.CreateCustomerRequest;
import com.keystone.fsm.dto.response.CustomerResponse;
import com.keystone.fsm.security.KeystonePrincipal;
import com.keystone.fsm.service.CustomerService;
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

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER')")
    @Operation(summary = "Create a new customer")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createCustomer(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER')")
    @Operation(summary = "List customers with optional search and pagination")
    public ResponseEntity<Page<CustomerResponse>> getCustomers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(customerService.getCustomers(search, pageable));
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in customer user's own organisation")
    public ResponseEntity<CustomerResponse> getMyOrganisation(
            @AuthenticationPrincipal KeystonePrincipal principal) {
        return ResponseEntity.ok(customerService.getCustomerForAccount(principal.getCustomerId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER')")
    @Operation(summary = "Get a customer by id")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable String id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'MANAGER')")
    @Operation(summary = "Update a customer")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable String id,
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }
}
