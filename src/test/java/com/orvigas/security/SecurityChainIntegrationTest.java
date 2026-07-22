package com.orvigas.security;

import com.orvigas.auth.LoginRequest;
import com.orvigas.auth.LoginResponse;
import com.orvigas.security.jwt.JwtService;
import com.orvigas.security.support.AbstractSecurityIntegrationTest;
import com.orvigas.security.support.JwtTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the reactive JWT security chain.
 *
 * @author orvigas@gmail.com
 */
@AutoConfigureWebTestClient
class SecurityChainIntegrationTest extends AbstractSecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtService jwtService;

    @Test
    void loginWithValidCredentialsReturnsToken() {
        webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest(TEST_USERNAME, testPassword))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .value(response -> {
                    assertThat(response.accessToken()).isNotBlank();
                    assertThat(response.tokenType()).isEqualTo("Bearer");
                    assertThat(response.expiresIn()).isEqualTo(900L);
                });
    }

    @Test
    void loginWithInvalidPasswordReturns401ProblemDetail() {
        webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest(TEST_USERNAME, "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void loginWithUnknownUsernameReturns401ProblemDetail() {
        webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest("unknown-user", testPassword))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void protectedEndpointWithoutAuthReturns401ProblemDetail() {
        webTestClient.get().uri("/secure")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void protectedEndpointWithValidTokenReturns200() {
        String token = jwtService.createToken(TEST_USERNAME, List.of("USER")).block();

        webTestClient.get().uri("/secure")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }

    @Test
    void protectedEndpointWithTamperedTokenReturns401ProblemDetail() {
        String token = jwtService.createToken(TEST_USERNAME, List.of("USER")).block();
        String tampered = JwtTestUtils.tamperPayload(token);

        webTestClient.get().uri("/secure")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void actuatorHealthIsPublic() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
