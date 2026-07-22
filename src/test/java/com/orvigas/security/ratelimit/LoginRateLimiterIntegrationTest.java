package com.orvigas.security.ratelimit;

import com.orvigas.auth.LoginRequest;
import com.orvigas.security.support.AbstractSecurityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for Resilience4j rate limiting on the login endpoint.
 *
 * @author orvigas@gmail.com
 */
@AutoConfigureWebTestClient
class LoginRateLimiterIntegrationTest extends AbstractSecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void rateLimitSettings(DynamicPropertyRegistry registry) {
        registry.add("resilience4j.ratelimiter.instances.login.limit-for-period", () -> 2);
    }

    @Test
    void loginRateLimiterBlocksAfterLimit() {
        for (int i = 0; i < 2; i++) {
            webTestClient.post().uri("/auth/login")
                    .bodyValue(new LoginRequest(TEST_USERNAME, testPassword))
                    .exchange()
                    .expectStatus().isOk();
        }

        webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest(TEST_USERNAME, testPassword))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
