package com.keystone.fsm.controller;

import com.keystone.fsm.dto.request.CreateUserRequest;
import com.keystone.fsm.dto.request.UpdateUserRequest;
import com.keystone.fsm.dto.response.UserResponse;
import com.keystone.fsm.security.KeystonePrincipal;
import com.keystone.fsm.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new user (MANAGER only)")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "List all users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "List active technicians")
    public ResponseEntity<List<UserResponse>> getTechnicians() {
        return ResponseEntity.ok(userService.getUsersByRole(com.keystone.fsm.domain.enums.Role.TECHNICIAN));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER') or authentication.principal.id == #id")
    @Operation(summary = "Get a user by id")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a user's name, role or enabled state (MANAGER only)")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal KeystonePrincipal principal) {
        return ResponseEntity.ok(userService.updateUser(id, request, principal.getId()));
    }
}
