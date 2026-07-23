package com.orvigas.payment.api;

import com.orvigas.security.jwt.JwtService;
import com.orvigas.security.support.AbstractSecurityIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for Resilience4j rate limiting on the payment endpoints.
 *
 * <p>Unlike the login limiter, the payment limiter's instance name is
 * resolved per call via SpEL over the caller's merchant id (see
 * {@code PaymentRestApiService}), so it can't be targeted by name the way
 * {@code resilience4j.ratelimiter.instances.login} is. Overriding {@code
 * configs.default} instead caps every dynamically created instance,
 * including whichever one this test's merchant id resolves to.
 *
 * @author orvigas@gmail.com
 */
@AutoConfigureWebTestClient
class PaymentRateLimiterIntegrationTest extends AbstractSecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtService jwtService;

    @DynamicPropertySource
    static void rateLimitSettings(DynamicPropertyRegistry registry) {
        registry.add("resilience4j.ratelimiter.configs.default.limit-for-period", () -> 2);
    }

    private String token() {
        return jwtService.createToken(TEST_USERNAME, List.of("USER"), testMerchantId).block();
    }

    @Test
    void paymentRateLimiterBlocksAfterLimit() {
        for (int i = 0; i < 2; i++) {
            webTestClient.post().uri("/payments")
                    .header("Authorization", "Bearer " + token())
                    .bodyValue(new InitiatePaymentRequest(
                            testMerchantId,
                            UUID.randomUUID().toString(),
                            new MoneyRequest(1000, "USD"),
                            "tok_visa",
                            UUID.randomUUID().toString()))
                    .exchange()
                    .expectStatus().isCreated();
        }

        webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .bodyValue(new InitiatePaymentRequest(
                        testMerchantId,
                        UUID.randomUUID().toString(),
                        new MoneyRequest(1000, "USD"),
                        "tok_visa",
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
