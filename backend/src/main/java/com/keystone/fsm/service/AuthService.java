package com.keystone.fsm.service;

import com.keystone.fsm.domain.model.User;
import com.keystone.fsm.dto.request.LoginRequest;
import com.keystone.fsm.dto.response.AuthResponse;
import com.keystone.fsm.dto.response.UserResponse;
import com.keystone.fsm.security.JwtTokenProvider;
import com.keystone.fsm.security.KeystonePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        KeystonePrincipal principal = (KeystonePrincipal) auth.getPrincipal();
        String token = jwtTokenProvider.generateToken(principal);
        User user = userService.getUserEntityById(principal.getId());
        return AuthResponse.of(token, user);
    }

    public UserResponse currentUser(KeystonePrincipal principal) {
        return userService.getUserById(principal.getId());
    }
}
