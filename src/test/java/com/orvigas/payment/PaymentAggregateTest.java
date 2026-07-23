package com.orvigas.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
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

    @Test
    @DisplayName("initiates a new payment")
    void testInitiatePayment() {
        var paymentId = PaymentId.newId();
        var merchantId = MerchantId.newId();
        var customerId = CustomerId.newId();
        var amount = Money.of(10000, "USD");
        var paymentMethod = new PaymentMethod("tok_visa");
        var expiresAt = Instant.now().plusSeconds(86400 * 7);

        var command = new InitiatePaymentCommand(
                paymentId, merchantId, customerId, amount, paymentMethod, "idempotency-key-1", expiresAt);

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
        var merchantId = MerchantId.newId();
        var customerId = CustomerId.newId();
        var amount = Money.of(10000, "USD");
        var paymentMethod = new PaymentMethod("tok_visa");
        var expiresAt = Instant.now().plusSeconds(86400 * 7);

        var initEvent = new PaymentInitiated(
                paymentId,
                merchantId,
                customerId,
                amount,
                paymentMethod,
                "key-1",
                expiresAt,
                Instant.now());

        var authCommand = new AuthorizePaymentCommand(paymentId, amount, "auth-code-123", null);

        fixture.given(initEvent)
                .when(authCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED));
    }

    @Test
    @DisplayName("records authorization failure")
    void testAuthorizePaymentFailure() {
        var paymentId = PaymentId.newId();
        var merchantId = MerchantId.newId();
        var customerId = CustomerId.newId();
        var amount = Money.of(10000, "USD");
        var paymentMethod = new PaymentMethod("tok_visa");
        var expiresAt = Instant.now().plusSeconds(86400 * 7);

        var initEvent = new PaymentInitiated(
                paymentId,
                merchantId,
                customerId,
                amount,
                paymentMethod,
                "key-1",
                expiresAt,
                Instant.now());

        var failureReason = new FailureReason("DECLINED", "Card declined");
        var authCommand = new AuthorizePaymentCommand(paymentId, amount, null, failureReason);

        fixture.given(initEvent)
                .when(authCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED));
    }


    @Test
    @DisplayName("fails payment with reason")
    void testFailPayment() {
        var paymentId = PaymentId.newId();
        var merchantId = MerchantId.newId();
        var customerId = CustomerId.newId();
        var amount = Money.of(10000, "USD");
        var paymentMethod = new PaymentMethod("tok_visa");
        var expiresAt = Instant.now().plusSeconds(86400 * 7);

        var initEvent = new PaymentInitiated(
                paymentId,
                merchantId,
                customerId,
                amount,
                paymentMethod,
                "key-1",
                expiresAt,
                Instant.now());

        var reason = new FailureReason("ERROR", "Payment processing failed");
        var failCommand = new FailPaymentCommand(paymentId, reason);

        fixture.given(initEvent)
                .when(failCommand)
                .expectSuccessfulHandlerExecution()
                .expectState(p -> {
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
                    assertThat(p.getFailureReason()).isEqualTo(reason);
                });
    }

    @Test
    @DisplayName("rejects capture exceeding authorized amount")
    void testCaptureBoundEnforced() {
        var paymentId = PaymentId.newId();
        var merchantId = MerchantId.newId();
        var customerId = CustomerId.newId();
        var amount = Money.of(10000, "USD");
        var authorizedAmount = Money.of(5000, "USD");
        var paymentMethod = new PaymentMethod("tok_visa");
        var expiresAt = Instant.now().plusSeconds(86400 * 7);
        var now = Instant.now();

        var initEvent = new PaymentInitiated(
                paymentId,
                merchantId,
                customerId,
                amount,
                paymentMethod,
                "key-1",
                expiresAt,
                now);
        var authEvent = new PaymentAuthorized(paymentId, authorizedAmount, "auth-code-123", null, now);

        var captureCommand = new CapturePaymentCommand(paymentId, amount, true);

        fixture.given(initEvent, authEvent)
                .when(captureCommand)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects refund exceeding captured amount")
    void testRefundBoundEnforced() {
        var paymentId = PaymentId.newId();
        var merchantId = MerchantId.newId();
        var customerId = CustomerId.newId();
        var capturedAmount = Money.of(5000, "USD");
        var refundAmount = Money.of(10000, "USD");
        var paymentMethod = new PaymentMethod("tok_visa");
        var expiresAt = Instant.now().plusSeconds(86400 * 7);
        var now = Instant.now();

        var initEvent = new PaymentInitiated(
                paymentId,
                merchantId,
                customerId,
                capturedAmount,
                paymentMethod,
                "key-1",
                expiresAt,
                now);
        var authEvent = new PaymentAuthorized(paymentId, capturedAmount, "auth-code-123", null, now);
        var chargeEvent = new PaymentCharged(
                paymentId,
                com.orvigas.shared.id.CaptureId.newId(),
                capturedAmount,
                true,
                now);

        var reason = RefundReason.of(RefundReasonCode.DUPLICATE);
        var refundCommand = new RefundPaymentCommand(paymentId, refundAmount, reason, "refund-key-1");

        fixture.given(initEvent, authEvent, chargeEvent)
                .when(refundCommand)
                .expectException(IllegalStateException.class);
    }
}
