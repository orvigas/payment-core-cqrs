package com.orvigas.security.error;

import com.orvigas.auth.LoginRequest;
import com.orvigas.security.support.AbstractSecurityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies malformed and invalid login requests get a fixed, generic RFC 7807
 * detail message rather than an echo of the underlying parser or validation
 * exception, which can carry internal detail or fragments of the request body.
 *
 * @author orvigas@gmail.com
 */
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GlobalErrorHandlerIntegrationTest extends AbstractSecurityIntegrationTest {

    private final WebTestClient webTestClient;

    GlobalErrorHandlerIntegrationTest(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    @Test
    void malformedJsonBodyReturnsGenericDetailWithoutLeakingParserInternals() {
        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not valid json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(ProblemDetail.class)
                .value(problem -> assertThat(problem.detail()).isEqualTo("Invalid request content"));
    }

    @Test
    void blankUsernameFailsBeanValidationWithGenericDetail() {
        webTestClient.post().uri("/auth/login")
                .bodyValue(new LoginRequest("", "irrelevant-password"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(ProblemDetail.class)
                .value(problem -> assertThat(problem.detail()).isEqualTo("Invalid request content"));
    }
}
