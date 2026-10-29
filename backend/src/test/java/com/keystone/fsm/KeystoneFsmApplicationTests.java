package com.keystone.fsm;

import com.keystone.fsm.dto.request.LoginRequest;
import com.keystone.fsm.dto.response.AuthResponse;
import com.keystone.fsm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KeystoneFsmApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void useJdkHttpClient() {
        // HttpURLConnection throws HttpRetryException on a streamed 401; java.net.http does not.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void contextLoads() {
        assertThat(userRepository).isNotNull();
    }

    @Test
    void loginWithSeedManagerSucceeds() {
        LoginRequest login = new LoginRequest("manager@keystone.com", "Manager1!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/login", login, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
    }

    @Test
    void loginWithBadCredentialsReturns401() {
        LoginRequest login = new LoginRequest("manager@keystone.com", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", login, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
