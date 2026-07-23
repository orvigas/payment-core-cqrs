package com.orvigas.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.orvigas.payment.AuthorizePaymentCommand;
import com.orvigas.payment.ConfirmCaptureCommand;
import com.orvigas.security.jwt.JwtService;
import com.orvigas.security.support.AbstractSecurityIntegrationTest;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for the Payment REST API. Exercises the full reactive
 * chain: JWT authentication, merchant-scoped authorization, request
 * validation, Axon command dispatch, aggregate processing, and RFC 7807
 * error handling.
 *
 * @author orvigas@gmail.com
 */
@AutoConfigureWebTestClient
class PaymentRestApiIntegrationTest extends AbstractSecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CommandGateway commandGateway;

    private String token() {
        return jwtService.createToken(TEST_USERNAME, List.of("USER"), testMerchantId).block();
    }

    private String tokenForMerchant(String merchantId) {
        return jwtService.createToken(TEST_USERNAME, List.of("USER"), merchantId).block();
    }

    @Test
    void initiatePaymentReturns201WithPaymentId() {
        var idempotencyKey = UUID.randomUUID().toString();

        var response = webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .bodyValue(new InitiatePaymentRequest(
                        testMerchantId,
                        UUID.randomUUID().toString(),
                        new MoneyRequest(10000, "USD"),
                        "tok_visa",
                        idempotencyKey))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(InitiatePaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isNotNull();
        assertThat(response.status()).isEqualTo("INITIATED");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void initiateForAnotherMerchantReturns403() {
        webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .bodyValue(new InitiatePaymentRequest(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        new MoneyRequest(10000, "USD"),
                        "tok_visa",
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void duplicateIdempotencyKeyReturns200() {
        var idempotencyKey = UUID.randomUUID().toString();
        var request = new InitiatePaymentRequest(
                testMerchantId,
                UUID.randomUUID().toString(),
                new MoneyRequest(10000, "USD"),
                "tok_visa",
                idempotencyKey);

        var first = webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(InitiatePaymentResponse.class)
                .returnResult()
                .getResponseBody();

        var second = webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(InitiatePaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(second).isNotNull();
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
    }

    @Test
    void invalidAmountReturns400() {
        webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .bodyValue(new InitiatePaymentRequest(
                        testMerchantId,
                        UUID.randomUUID().toString(),
                        new MoneyRequest(0, "USD"),
                        "tok_visa",
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void missingRequestBodyReturns400() {
        webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void unauthenticatedRequestReturns401() {
        webTestClient.post().uri("/payments")
                .bodyValue(new InitiatePaymentRequest(
                        testMerchantId,
                        UUID.randomUUID().toString(),
                        new MoneyRequest(10000, "USD"),
                        "tok_visa",
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void captureUnknownPaymentReturns404() {
        var unknownId = UUID.randomUUID();

        webTestClient.post().uri("/payments/{paymentId}/captures", unknownId)
                .header("Authorization", "Bearer " + token())
                .bodyValue(new CapturePaymentRequest(new MoneyRequest(1000, "USD"), true))
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void refundUnknownPaymentReturns404() {
        var unknownId = UUID.randomUUID();

        webTestClient.post().uri("/payments/{paymentId}/refunds", unknownId)
                .header("Authorization", "Bearer " + token())
                .bodyValue(new RefundPaymentRequest(
                        new MoneyRequest(1000, "USD"),
                        new RefundReasonRequest("REQUESTED_BY_CUSTOMER", "Test refund"),
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void captureForAnotherMerchantReturns404() {
        var paymentId = PaymentId.fromString(
                createInitiatedPayment().paymentId().toString());
        authorizePayment(paymentId, 10000);

        webTestClient.post().uri("/payments/{paymentId}/captures", paymentId.value())
                .header("Authorization", "Bearer " + tokenForMerchant(UUID.randomUUID().toString()))
                .bodyValue(new CapturePaymentRequest(new MoneyRequest(10000, "USD"), true))
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void refundForAnotherMerchantReturns404() {
        var paymentId = PaymentId.fromString(
                createInitiatedPayment().paymentId().toString());
        authorizePayment(paymentId, 10000);
        var captureResponse = capturePayment(paymentId, 10000, true);
        confirmCapture(paymentId, captureResponse);

        webTestClient.post().uri("/payments/{paymentId}/refunds", paymentId.value())
                .header("Authorization", "Bearer " + tokenForMerchant(UUID.randomUUID().toString()))
                .bodyValue(new RefundPaymentRequest(
                        new MoneyRequest(5000, "USD"),
                        new RefundReasonRequest("REQUESTED_BY_CUSTOMER", "Test refund"),
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void captureOnInitiatedPaymentReturns400() {
        var initiateResponse = createInitiatedPayment();

        webTestClient.post().uri("/payments/{paymentId}/captures", initiateResponse.paymentId())
                .header("Authorization", "Bearer " + token())
                .bodyValue(new CapturePaymentRequest(new MoneyRequest(5000, "USD"), false))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void refundOnInitiatedPaymentReturns400() {
        var initiateResponse = createInitiatedPayment();

        webTestClient.post().uri("/payments/{paymentId}/refunds", initiateResponse.paymentId())
                .header("Authorization", "Bearer " + token())
                .bodyValue(new RefundPaymentRequest(
                        new MoneyRequest(1000, "USD"),
                        new RefundReasonRequest("REQUESTED_BY_CUSTOMER", "Test refund"),
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void captureExceedingAuthorizedAmountReturns400() {
        var paymentId = PaymentId.fromString(
                createInitiatedPayment().paymentId().toString());
        authorizePayment(paymentId, 5000);

        webTestClient.post().uri("/payments/{paymentId}/captures", paymentId.value())
                .header("Authorization", "Bearer " + token())
                .bodyValue(new CapturePaymentRequest(new MoneyRequest(10000, "USD"), true))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void refundExceedingCapturedAmountReturns400() {
        var paymentId = PaymentId.fromString(
                createInitiatedPayment().paymentId().toString());
        authorizePayment(paymentId, 10000);
        var captureResponse = capturePayment(paymentId, 10000, true);
        confirmCapture(paymentId, captureResponse);

        webTestClient.post().uri("/payments/{paymentId}/refunds", paymentId.value())
                .header("Authorization", "Bearer " + token())
                .bodyValue(new RefundPaymentRequest(
                        new MoneyRequest(20000, "USD"),
                        new RefundReasonRequest("REQUESTED_BY_CUSTOMER", "Test refund"),
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void fullHappyPathInitiateCaptureRefund() {
        var idempotencyKey = UUID.randomUUID().toString();
        var initiateResponse = webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .bodyValue(new InitiatePaymentRequest(
                        testMerchantId,
                        UUID.randomUUID().toString(),
                        new MoneyRequest(10000, "USD"),
                        "tok_visa",
                        idempotencyKey))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(InitiatePaymentResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(initiateResponse).isNotNull();

        var paymentId = PaymentId.fromString(initiateResponse.paymentId().toString());
        authorizePayment(paymentId, 10000);

        var captureResponse = webTestClient.post()
                .uri("/payments/{paymentId}/captures", paymentId.value())
                .header("Authorization", "Bearer " + token())
                .bodyValue(new CapturePaymentRequest(new MoneyRequest(10000, "USD"), true))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CapturePaymentResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(captureResponse).isNotNull();
        assertThat(captureResponse.captureId()).isNotNull();
        // A capture only reaches PENDING here; SUCCEEDED requires a separate provider
        // confirmation via ConfirmCaptureCommand, dispatched below.
        assertThat(captureResponse.status()).isEqualTo("PENDING");

        confirmCapture(paymentId, captureResponse);

        var refundKey = UUID.randomUUID().toString();
        var refundResponse = webTestClient.post()
                .uri("/payments/{paymentId}/refunds", paymentId.value())
                .header("Authorization", "Bearer " + token())
                .bodyValue(new RefundPaymentRequest(
                        new MoneyRequest(5000, "USD"),
                        new RefundReasonRequest("REQUESTED_BY_CUSTOMER", "Customer requested"),
                        refundKey))
                .exchange()
                .expectStatus().isOk()
                .expectBody(RefundPaymentResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(refundResponse).isNotNull();
        assertThat(refundResponse.refundId()).isNotNull();
        assertThat(refundResponse.status()).isEqualTo("REFUND_REQUESTED");
        assertThat(refundResponse.amount().minorUnits()).isEqualTo(5000);
    }

    @Test
    void duplicateRefundIdempotencyReturns200() {
        var paymentId = PaymentId.fromString(
                createInitiatedPayment().paymentId().toString());
        authorizePayment(paymentId, 10000);
        var captureResponse = capturePayment(paymentId, 10000, true);
        confirmCapture(paymentId, captureResponse);

        var refundKey = UUID.randomUUID().toString();
        var refundRequest = new RefundPaymentRequest(
                new MoneyRequest(5000, "USD"),
                new RefundReasonRequest("REQUESTED_BY_CUSTOMER", "Test"),
                refundKey);

        var first = webTestClient.post()
                .uri("/payments/{paymentId}/refunds", paymentId.value())
                .header("Authorization", "Bearer " + token())
                .bodyValue(refundRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(RefundPaymentResponse.class)
                .returnResult()
                .getResponseBody();

        var second = webTestClient.post()
                .uri("/payments/{paymentId}/refunds", paymentId.value())
                .header("Authorization", "Bearer " + token())
                .bodyValue(refundRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(RefundPaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.status()).isEqualTo("REFUND_REQUESTED");
        // The bug this guards against: the aggregate discards the freshly generated id on a
        // duplicate key and returns the original refund's id instead - the service must build
        // the response from that returned id, not echo a new one it minted locally.
        assertThat(second.refundId()).isEqualTo(first.refundId());
    }

    private InitiatePaymentResponse createInitiatedPayment() {
        return webTestClient.post().uri("/payments")
                .header("Authorization", "Bearer " + token())
                .bodyValue(new InitiatePaymentRequest(
                        testMerchantId,
                        UUID.randomUUID().toString(),
                        new MoneyRequest(10000, "USD"),
                        "tok_visa",
                        UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(InitiatePaymentResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void authorizePayment(PaymentId paymentId, long minorUnits) {
        commandGateway.send(
                new AuthorizePaymentCommand(
                        paymentId,
                        Money.of(minorUnits, "USD"),
                        "auth-code-" + UUID.randomUUID(),
                        null)).join();
    }

    private CapturePaymentResponse capturePayment(PaymentId paymentId, long minorUnits, boolean isFinal) {
        return webTestClient.post()
                .uri("/payments/{paymentId}/captures", paymentId.value())
                .header("Authorization", "Bearer " + token())
                .bodyValue(new CapturePaymentRequest(new MoneyRequest(minorUnits, "USD"), isFinal))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CapturePaymentResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void confirmCapture(PaymentId paymentId, CapturePaymentResponse captureResponse) {
        commandGateway.send(
                new ConfirmCaptureCommand(
                        paymentId,
                        com.orvigas.shared.id.CaptureId.fromString(
                                captureResponse.captureId().toString()),
                        "provider-ref-" + UUID.randomUUID())).join();
    }
}
