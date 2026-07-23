package com.orvigas.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Payment aggregate using Axon test fixtures.
 *
 * @author orvigas@gmail.com
 */
@DisplayName("Payment aggregate")
class PaymentAggregateTest {

    private FixtureConfiguration<Payment> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Payment.class);
    }

    private PaymentInitiated initiatedEvent(PaymentId paymentId, Money amount, Instant now) {
        return new PaymentInitiated(
                paymentId,
                MerchantId.newId(),
                CustomerId.newId(),
                amount,
                new PaymentMethod("tok_visa"),
                "key-1",
                now.plusSeconds(86400 * 7),
                now);
    }

    private PaymentAuthorized authorizedEvent(PaymentId paymentId, Money amount, Instant now) {
        return new PaymentAuthorized(paymentId, amount, "auth-code-123", null, now);
    }

    private PaymentCharged chargedEvent(
            PaymentId paymentId, CaptureId captureId, Money amount, boolean isFinal, Instant now) {
        return new PaymentCharged(paymentId, captureId, amount, isFinal, now);
    }

    private CaptureSucceeded captureSucceededEvent(PaymentId paymentId, CaptureId captureId, Instant now) {
        return new CaptureSucceeded(paymentId, captureId, "provider-ref", now);
    }

    private CaptureFailed captureFailedEvent(PaymentId paymentId, CaptureId captureId, Instant now) {
        return new CaptureFailed(paymentId, captureId, new FailureReason("DECLINED", "Declined"), now);
    }

    private RefundRequested refundRequestedEvent(
            PaymentId paymentId,
            RefundId refundId,
            Money amount,
            String idempotencyKey,
            Instant now) {
        return new RefundRequested(
                paymentId,
                refundId,
                amount,
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                idempotencyKey,
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"),
                now);
    }

    private RefundSucceeded refundSucceededEvent(PaymentId paymentId, RefundId refundId, Instant now) {
        return new RefundSucceeded(paymentId, refundId, "provider-ref", now);
    }

    @Test
    @DisplayName("creates a new payment")
    void testCreatePayment() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var paymentMethod = new PaymentMethod("tok_visa");
        var expiresAt = Instant.now().plusSeconds(86400 * 7);

        var command = new CreatePaymentCommand(
                paymentId,
                MerchantId.newId(),
                CustomerId.newId(),
                amount,
                paymentMethod,
                "idempotency-key-1",
                expiresAt);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.INITIATED);
                    assertThat(p.getPaymentId()).isEqualTo(paymentId);
                    assertThat(p.getAmount()).isEqualTo(amount);
                });
    }

    @Test
    @DisplayName("authorizes a payment successfully")
    void testAuthorizePaymentSuccess() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();

        var authCommand = new AuthorizePaymentCommand(paymentId, amount, "auth-code-123", null);

        fixture.given(initiatedEvent(paymentId, amount, now))
                .when(authCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED));
    }

    @Test
    @DisplayName("records authorization failure")
    void testAuthorizePaymentFailure() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();

        var failureReason = new FailureReason("DECLINED", "Card declined");
        var authCommand = new AuthorizePaymentCommand(paymentId, amount, null, failureReason);

        fixture.given(initiatedEvent(paymentId, amount, now))
                .when(authCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    @DisplayName("rejects authorization when not in INITIATED state")
    void testAuthorizeOnlyInInitiatedState() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();

        var authCommand = new AuthorizePaymentCommand(paymentId, amount, "auth-code-123", null);

        fixture.given(initiatedEvent(paymentId, amount, now), authorizedEvent(paymentId, amount, now))
                .when(authCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("captures the full authorized amount")
    void testFullCapture() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        var captureCommand = new CapturePaymentCommand(paymentId, amount, true);

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now))
                .when(new ConfirmCaptureCommand(paymentId, captureId, "provider-ref"))
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
                    assertThat(p.getCapturedAmount()).isEqualTo(amount);
                });
    }

    @Test
    @DisplayName("captures a partial amount and leaves the payment partially captured")
    void testPartialCapture() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var partialAmount = Money.of(5000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, partialAmount, false, now))
                .when(new ConfirmCaptureCommand(paymentId, captureId, "provider-ref"))
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CAPTURED);
                    assertThat(p.getCapturedAmount()).isEqualTo(partialAmount);
                });
    }

    @Test
    @DisplayName("rejects a capture that exceeds the authorized amount")
    void testCaptureBoundEnforced() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var authorizedAmount = Money.of(5000, "USD");
        var now = Instant.now();

        var captureCommand = new CapturePaymentCommand(paymentId, amount, true);

        fixture.given(initiatedEvent(paymentId, amount, now), authorizedEvent(paymentId, authorizedAmount, now))
                .when(captureCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("counts pending captures against the authorized amount")
    void testPendingCaptureCountedAgainstAuthorizedAmount() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        var secondCaptureCommand = new CapturePaymentCommand(paymentId, amount, true);

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now))
                .when(secondCaptureCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects a capture after a final capture has succeeded")
    void testCaptureAfterFinalCaptureRejected() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        var secondCaptureCommand = new CapturePaymentCommand(paymentId, Money.of(1, "USD"), false);

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now))
                .when(secondCaptureCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects a capture after the authorization has expired")
    void testCaptureAfterExpiryRejected() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var expiredExpiresAt = now.minusSeconds(1);
        var initEvent = new PaymentInitiated(
                paymentId,
                MerchantId.newId(),
                CustomerId.newId(),
                amount,
                new PaymentMethod("tok_visa"),
                "key-1",
                expiredExpiresAt,
                now.minusSeconds(86400));

        var captureCommand = new CapturePaymentCommand(paymentId, amount, true);

        fixture.given(initEvent, authorizedEvent(paymentId, amount, now.minusSeconds(86400)))
                .when(captureCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("records a failed capture")
    void testCaptureFailed() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now))
                .when(new FailCaptureCommand(paymentId, captureId, new FailureReason("DECLINED", "Declined")))
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
                    assertThat(p.getCapturedAmount().isZero()).isTrue();
                });
    }

    @Test
    @DisplayName("completes a captured payment")
    void testCompletePayment() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now))
                .when(new CompletePaymentCommand(paymentId))
                .expectSuccessfulHandlerExecution()
                .expectState(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED));
    }

    @Test
    @DisplayName("rejects completion when not captured")
    void testCompleteOnlyWhenCaptured() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();

        fixture.given(initiatedEvent(paymentId, amount, now), authorizedEvent(paymentId, amount, now))
                .when(new CompletePaymentCommand(paymentId))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("fails a payment with reason")
    void testFailPayment() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();

        var reason = new FailureReason("ERROR", "Payment processing failed");
        var failCommand = new FailPaymentCommand(paymentId, reason);

        fixture.given(initiatedEvent(paymentId, amount, now))
                .when(failCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
                    assertThat(p.getFailureReason()).isEqualTo(reason);
                });
    }

    @Test
    @DisplayName("rejects fail on a terminal payment")
    void testFailTerminalStateRejected() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        var reason = new FailureReason("ERROR", "Payment processing failed");
        var failCommand = new FailPaymentCommand(paymentId, reason);

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        new PaymentCompleted(paymentId, now))
                .when(failCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("expires an authorized payment after the deadline")
    void testExpirePayment() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var expiredExpiresAt = now.minusSeconds(1);
        var initEvent = new PaymentInitiated(
                paymentId,
                MerchantId.newId(),
                CustomerId.newId(),
                amount,
                new PaymentMethod("tok_visa"),
                "key-1",
                expiredExpiresAt,
                now.minusSeconds(86400));

        fixture.given(initEvent, authorizedEvent(paymentId, amount, now.minusSeconds(86400)))
                .when(new ExpirePaymentCommand(paymentId))
                .expectSuccessfulHandlerExecution()
                .expectState(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.EXPIRED));
    }

    @Test
    @DisplayName("rejects expiration before the deadline")
    void testExpireBeforeDeadlineRejected() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();

        fixture.given(initiatedEvent(paymentId, amount, now), authorizedEvent(paymentId, amount, now))
                .when(new ExpirePaymentCommand(paymentId))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("records a refund request against a captured payment")
    void testRefundPayment() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        var refundCommand = new RefundPaymentCommand(
                paymentId,
                amount,
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                "refund-key-1",
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"));

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now))
                .when(refundCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
                    assertThat(p.getRefunds()).hasSize(1);
                });
    }

    @Test
    @DisplayName("records a refund request against a completed payment")
    void testRefundCompletedPayment() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        var refundCommand = new RefundPaymentCommand(
                paymentId,
                amount,
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                "refund-key-1",
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"));

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        new PaymentCompleted(paymentId, now))
                .when(refundCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
                    assertThat(p.getRefunds()).hasSize(1);
                });
    }

    @Test
    @DisplayName("rejects a refund exceeding the captured amount")
    void testRefundBoundEnforced() {
        var paymentId = PaymentId.newId();
        var capturedAmount = Money.of(5000, "USD");
        var refundAmount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();

        var refundCommand = new RefundPaymentCommand(
                paymentId,
                refundAmount,
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                "refund-key-1",
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"));

        fixture.given(
                        initiatedEvent(paymentId, capturedAmount, now),
                        authorizedEvent(paymentId, capturedAmount, now),
                        chargedEvent(paymentId, captureId, capturedAmount, true, now),
                        captureSucceededEvent(paymentId, captureId, now))
                .when(refundCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects a refund on an unauthorized payment")
    void testRefundRequiresCapturedFunds() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();

        var refundCommand = new RefundPaymentCommand(
                paymentId,
                amount,
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                "refund-key-1",
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"));

        fixture.given(initiatedEvent(paymentId, amount, now))
                .when(refundCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("is idempotent on refund requests with the same key and amount")
    void testRefundIdempotency() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();
        var refundId = RefundId.newId();

        var refundCommand = new RefundPaymentCommand(
                paymentId,
                amount,
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                "refund-key-1",
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"));

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        refundRequestedEvent(paymentId, refundId, amount, "refund-key-1", now))
                .when(refundCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> assertThat(p.getRefunds()).containsKey(refundId));
    }

    @Test
    @DisplayName("rejects a refund with the same key but a different amount")
    void testRefundIdempotencyConflict() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();
        var refundId = RefundId.newId();

        var refundCommand = new RefundPaymentCommand(
                paymentId,
                Money.of(5000, "USD"),
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                "refund-key-1",
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"));

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        refundRequestedEvent(paymentId, refundId, amount, "refund-key-1", now))
                .when(refundCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects failing a payment that is already partially refunded")
    void testFailPartiallyRefundedPaymentRejected() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var refundAmount = Money.of(5000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();
        var refundId = RefundId.newId();

        var failCommand = new FailPaymentCommand(paymentId, new FailureReason("ERROR", "Failed"));

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        refundRequestedEvent(paymentId, refundId, refundAmount, "refund-key-1", now),
                        refundSucceededEvent(paymentId, refundId, now))
                .when(failCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("confirms a refund after marking it pending")
    void testRefundPendingAndConfirm() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();
        var refundId = RefundId.newId();

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        refundRequestedEvent(paymentId, refundId, amount, "refund-key-1", now),
                        new RefundPending(paymentId, refundId, now))
                .when(new ConfirmRefundCommand(paymentId, refundId, "provider-ref"))
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
                    assertThat(p.getRefundedAmount()).isEqualTo(amount);
                });
    }

    @Test
    @DisplayName("rejects confirming a refund that is already succeeded")
    void testConfirmAlreadySucceededRefundRejected() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();
        var refundId = RefundId.newId();

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        refundRequestedEvent(paymentId, refundId, amount, "refund-key-1", now),
                        refundSucceededEvent(paymentId, refundId, now))
                .when(new ConfirmRefundCommand(paymentId, refundId, "provider-ref"))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects a capture whose caller is not the payment's merchant")
    void testCaptureRejectedForWrongMerchant() {
        var paymentId = PaymentId.newId();
        var merchantId = MerchantId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var initEvent = new PaymentInitiated(
                paymentId,
                merchantId,
                CustomerId.newId(),
                amount,
                new PaymentMethod("tok_visa"),
                "key-1",
                now.plusSeconds(86400 * 7),
                now);

        var captureCommand = new CapturePaymentCommand(paymentId, amount, true, null, MerchantId.newId());

        fixture.given(initEvent, authorizedEvent(paymentId, amount, now))
                .when(captureCommand)
                .expectException(PaymentAccessDeniedException.class);
    }

    @Test
    @DisplayName("captures successfully when the caller is the payment's own merchant")
    void testCaptureAllowedForOwningMerchant() {
        var paymentId = PaymentId.newId();
        var merchantId = MerchantId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var initEvent = new PaymentInitiated(
                paymentId,
                merchantId,
                CustomerId.newId(),
                amount,
                new PaymentMethod("tok_visa"),
                "key-1",
                now.plusSeconds(86400 * 7),
                now);

        var captureCommand = new CapturePaymentCommand(paymentId, amount, true, null, merchantId);

        fixture.given(initEvent, authorizedEvent(paymentId, amount, now))
                .when(captureCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED));
    }

    @Test
    @DisplayName("rejects a refund whose caller is not the payment's merchant")
    void testRefundRejectedForWrongMerchant() {
        var paymentId = PaymentId.newId();
        var merchantId = MerchantId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();
        var initEvent = new PaymentInitiated(
                paymentId,
                merchantId,
                CustomerId.newId(),
                amount,
                new PaymentMethod("tok_visa"),
                "key-1",
                now.plusSeconds(86400 * 7),
                now);

        var refundCommand = new RefundPaymentCommand(
                paymentId,
                amount,
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                "refund-key-1",
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"),
                null,
                MerchantId.newId());

        fixture.given(
                        initEvent,
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now))
                .when(refundCommand)
                .expectException(PaymentAccessDeniedException.class);
    }

    @Test
    @DisplayName("returns the same refund id on a duplicate idempotency key")
    void testRefundIdempotencyReturnsExistingRefundId() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();
        var refundId = RefundId.newId();

        var refundCommand = new RefundPaymentCommand(
                paymentId,
                amount,
                null,
                RefundReason.of(RefundReasonCode.DUPLICATE),
                "refund-key-1",
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "user-1"));

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        refundRequestedEvent(paymentId, refundId, amount, "refund-key-1", now))
                .when(refundCommand)
                .expectSuccessfulHandlerExecution()
                .expectResultMessagePayload(refundId);
    }

    @Test
    @DisplayName("rejects failing a refund that is already failed")
    void testFailAlreadyFailedRefundRejected() {
        var paymentId = PaymentId.newId();
        var amount = Money.of(10000, "USD");
        var now = Instant.now();
        var captureId = CaptureId.newId();
        var refundId = RefundId.newId();

        fixture.given(
                        initiatedEvent(paymentId, amount, now),
                        authorizedEvent(paymentId, amount, now),
                        chargedEvent(paymentId, captureId, amount, true, now),
                        captureSucceededEvent(paymentId, captureId, now),
                        refundRequestedEvent(paymentId, refundId, amount, "refund-key-1", now),
                        new RefundFailed(paymentId, refundId, new FailureReason("DECLINED", "Declined"), now))
                .when(new FailRefundCommand(paymentId, refundId, new FailureReason("DECLINED", "Declined")))
                .expectException(IllegalStateException.class);
    }
}
