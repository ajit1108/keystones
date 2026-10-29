package com.keystone.fsm.service;

import com.keystone.fsm.domain.enums.Role;
import com.keystone.fsm.domain.model.User;
import com.keystone.fsm.dto.request.CreateUserRequest;
import com.keystone.fsm.dto.request.UpdateUserRequest;
import com.keystone.fsm.dto.response.UserResponse;
import com.keystone.fsm.exception.BusinessRuleException;
import com.keystone.fsm.exception.DuplicateResourceException;
import com.keystone.fsm.exception.ResourceNotFoundException;
import com.keystone.fsm.repository.CustomerRepository;
import com.keystone.fsm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse createUser(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("User already exists with email: " + email);
        }
        String customerId = resolveCustomerLink(request.role(), request.customerId());
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(request.role())
                .customerId(customerId)
                .enabled(true)
                .build();
        return toResponse(userRepository.save(user));
    }

    public UserResponse updateUser(String id, UpdateUserRequest request, String actingUserId) {
        User user = getUserEntityById(id);
        if (user.getId().equals(actingUserId) && !request.enabled()) {
            throw new BusinessRuleException("You cannot disable your own account");
        }
        if (user.getId().equals(actingUserId) && request.role() != Role.MANAGER) {
            throw new BusinessRuleException("You cannot remove your own manager role");
        }
        if (request.role() == Role.CUSTOMER && (user.getCustomerId() == null || user.getCustomerId().isBlank())) {
            throw new BusinessRuleException("Customer users must be linked to a customer organisation");
        }
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        if (request.role() != Role.CUSTOMER) {
            user.setCustomerId(null);
        }
        return toResponse(userRepository.save(user));
    }

    public UserResponse getUserById(String id) {
        return toResponse(getUserEntityById(id));
    }

    public User getUserEntityById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public List<UserResponse> getUsersByRole(Role role) {
        return userRepository.findByRoleAndEnabledTrue(role).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    private String resolveCustomerLink(Role role, String customerId) {
        if (role != Role.CUSTOMER) {
            return null;
        }
        if (customerId == null || customerId.isBlank()) {
            throw new BusinessRuleException("customerId is required for CUSTOMER users");
        }
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
        return customerId;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getFullName(),
                user.getRole(), user.getCustomerId(),
                user.isEnabled(), user.getCreatedAt());
    }
}
