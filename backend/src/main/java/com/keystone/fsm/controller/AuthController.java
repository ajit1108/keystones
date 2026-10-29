package com.keystone.fsm.controller;

import com.keystone.fsm.dto.request.LoginRequest;
import com.keystone.fsm.dto.request.RegisterRequest;
import com.keystone.fsm.dto.response.AuthResponse;
import com.keystone.fsm.dto.response.UserResponse;
import com.keystone.fsm.security.KeystonePrincipal;
import com.keystone.fsm.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT login and self-service registration")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Create a self-service customer account")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The signed-in user's profile")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal KeystonePrincipal principal) {
        return ResponseEntity.ok(authService.currentUser(principal));
    }
}
