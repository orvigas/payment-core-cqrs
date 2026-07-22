package com.orvigas.security.config;

import com.orvigas.security.support.AbstractSecurityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the public/authenticated split enforced by {@code SecurityConfig}'s
 * path allowlist.
 *
 * @author orvigas@gmail.com
 */
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityConfigIntegrationTest extends AbstractSecurityIntegrationTest {

    private final WebTestClient webTestClient;

    SecurityConfigIntegrationTest(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    @Test
    void actuatorInfoRequiresAuthentication() {
        webTestClient.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
