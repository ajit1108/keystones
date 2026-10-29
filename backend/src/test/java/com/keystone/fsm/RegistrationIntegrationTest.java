package com.keystone.fsm;

import com.fasterxml.jackson.databind.JsonNode;
import com.keystone.fsm.domain.model.User;
import com.keystone.fsm.repository.CustomerRepository;
import com.keystone.fsm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises public self-service registration through the real HTTP stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RegistrationIntegrationTest {

    private static final String PASSWORD = "Register1!";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void registrationCreatesCustomerAccountWithHashedPasswordAndOrganisation() {
        Account account = newAccount();

        ResponseEntity<JsonNode> response = register(account.fullName(), account.email(), PASSWORD, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(body.get("email").asText()).isEqualTo(account.email());
        assertThat(body.get("fullName").asText()).isEqualTo(account.fullName());
        assertThat(body.get("enabled").asBoolean()).isTrue();
        assertThat(body.has("passwordHash")).isFalse();

        User saved = userRepository.findByEmailIgnoreCase(account.email()).orElseThrow();
        assertThat(saved.getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getCustomerId()).isNotBlank();
        assertThat(customerRepository.findById(saved.getCustomerId())).isPresent();
    }

    @Test
    void registeredUserCanSignInWithMixedCaseAndPaddedEmail() {
        Account account = newAccount();
        assertThat(register(account.fullName(), account.email(), PASSWORD, PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        String messyEmail = "  " + account.email().toUpperCase(Locale.ROOT) + " ";
        ResponseEntity<JsonNode> login = restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", messyEmail, "password", PASSWORD), jsonHeaders()),
                JsonNode.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("token").asText()).isNotBlank();
        assertThat(login.getBody().get("role").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfCase() {
        Account account = newAccount();
        assertThat(register(account.fullName(), account.email(), PASSWORD, PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> duplicate = register(
                account.fullName() + " two",
                account.email().toUpperCase(Locale.ROOT),
                PASSWORD, PASSWORD);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("message").asText()).contains("already exists");
        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(account.email()))
                .count()).isEqualTo(1);
    }

    @Test
    void mismatchedPasswordConfirmationIsRejected() {
        Account account = newAccount();

        ResponseEntity<JsonNode> response = register(
                account.fullName(), account.email(), PASSWORD, PASSWORD + "x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).isEqualTo("Passwords do not match");
        assertThat(userRepository.existsByEmailIgnoreCase(account.email())).isFalse();
    }

    @Test
    void weakPasswordIsRejectedBeforeAnyWrite() {
        Account account = newAccount();

        ResponseEntity<JsonNode> response = register(
                account.fullName(), account.email(), "onlyletters", "onlyletters");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).isEqualTo("Validation failed");
        assertThat(response.getBody().get("fieldErrors").has("password")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase(account.email())).isFalse();
    }

    @Test
    void registrationCannotGrantStaffRoles() {
        Account account = newAccount();

        ResponseEntity<JsonNode> response = register(Map.of(
                "fullName", account.fullName(),
                "email", account.email(),
                "password", PASSWORD,
                "confirmPassword", PASSWORD,
                "role", "MANAGER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("role").asText()).isEqualTo("CUSTOMER");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record Account(String email, String fullName) {}

    private static Account newAccount() {
        String token = UUID.randomUUID().toString().substring(0, 8);
        return new Account("signup." + token + "@example.com", "Nora " + token);
    }

    private ResponseEntity<JsonNode> register(String fullName, String email, String password, String confirmPassword) {
        return register(Map.of(
                "fullName", fullName,
                "email", email,
                "password", password,
                "confirmPassword", confirmPassword));
    }

    private ResponseEntity<JsonNode> register(Map<String, String> body) {
        return restTemplate.postForEntity("/api/auth/register",
                new HttpEntity<>(body, jsonHeaders()), JsonNode.class);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
