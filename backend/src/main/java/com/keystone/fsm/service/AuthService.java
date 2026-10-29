package com.keystone.fsm.service;

import com.keystone.fsm.domain.enums.Role;
import com.keystone.fsm.domain.model.User;
import com.keystone.fsm.dto.request.CreateCustomerRequest;
import com.keystone.fsm.dto.request.CreateUserRequest;
import com.keystone.fsm.dto.request.LoginRequest;
import com.keystone.fsm.dto.request.RegisterRequest;
import com.keystone.fsm.dto.response.AuthResponse;
import com.keystone.fsm.dto.response.CustomerResponse;
import com.keystone.fsm.dto.response.UserResponse;
import com.keystone.fsm.exception.BusinessRuleException;
import com.keystone.fsm.exception.DuplicateResourceException;
import com.keystone.fsm.repository.CustomerRepository;
import com.keystone.fsm.repository.UserRepository;
import com.keystone.fsm.security.JwtTokenProvider;
import com.keystone.fsm.security.KeystonePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final CustomerService customerService;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password()));

        KeystonePrincipal principal = (KeystonePrincipal) auth.getPrincipal();
        String token = jwtTokenProvider.generateToken(principal);
        User user = userService.getUserEntityById(principal.getId());
        return AuthResponse.of(token, user);
    }

    /**
     * Self-service sign-up. Public callers always become CUSTOMER users linked to a
     * dedicated organisation, so registration can never grant a staff role.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessRuleException("Passwords do not match");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        String[] name = splitFullName(request.fullName());
        CustomerResponse organisation = customerService.createCustomer(
                new CreateCustomerRequest(uniqueOrganisationName(name), email, null, null));
        return userService.createUser(new CreateUserRequest(
                email, request.password(), name[0], name[1], Role.CUSTOMER, organisation.id()));
    }

    public UserResponse currentUser(KeystonePrincipal principal) {
        return userService.getUserById(principal.getId());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String[] splitFullName(String fullName) {
        String name = fullName.trim().replaceAll("\\s+", " ");
        int split = name.indexOf(' ');
        if (split < 1 || split == name.length() - 1) {
            throw new BusinessRuleException("Enter your first and last name");
        }
        String firstName = name.substring(0, split);
        String lastName = name.substring(split + 1);
        if (firstName.length() > 80 || lastName.length() > 80) {
            throw new BusinessRuleException("First and last name must each be 80 characters or fewer");
        }
        return new String[]{firstName, lastName};
    }

    private String uniqueOrganisationName(String[] name) {
        String base = truncate(name[0] + " " + name[1], 120);
        String candidate = base;
        int suffix = 2;
        while (customerRepository.existsByName(candidate)) {
            String tail = " " + suffix++;
            candidate = truncate(base, 120 - tail.length()) + tail;
        }
        return candidate;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }
}
