package com.orvigas.payment.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orvigas.payment.CaptureFailed;
import com.orvigas.payment.CaptureSucceeded;
import com.orvigas.payment.FailureReason;
import com.orvigas.payment.PaymentAuthorized;
import com.orvigas.payment.PaymentCharged;
import com.orvigas.payment.PaymentCompleted;
import com.orvigas.payment.PaymentExpired;
import com.orvigas.payment.PaymentFailed;
import com.orvigas.payment.PaymentInitiated;
import com.orvigas.payment.PaymentMethod;
import com.orvigas.payment.RefundFailed;
import com.orvigas.payment.RefundInitiator;
import com.orvigas.payment.RefundInitiatorType;
import com.orvigas.payment.RefundPending;
import com.orvigas.payment.RefundReason;
import com.orvigas.payment.RefundReasonCode;
import com.orvigas.payment.RefundRequested;
import com.orvigas.payment.RefundSucceeded;
import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import com.orvigas.shared.money.Money;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies each Kafka payload correctly translates its domain event and
 * exposes the right topic, event type, and schema version.
 *
 * @author orvigas@gmail.com
 */
@DisplayName("Payment Kafka event payloads")
class PaymentKafkaEventPayloadTest {

    private final PaymentId paymentId = PaymentId.newId();
    private final Instant occurredAt = Instant.parse("2026-07-22T10:15:30Z");

    @Test
    @DisplayName("PaymentInitiated maps onto payment-initiated without the payment method token")
    void testPaymentInitiated() {
        var event = new PaymentInitiated(
                paymentId,
                MerchantId.newId(),
                CustomerId.newId(),
                Money.of(10000, "USD"),
                new PaymentMethod("tok_visa"),
                "idem-key",
                occurredAt.plusSeconds(3600),
                occurredAt);

        var payload = PaymentInitiatedPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_INITIATED);
        assertThat(payload.eventType()).isEqualTo("PaymentInitiated");
        assertThat(payload.schemaVersion()).isEqualTo(1);
        assertThat(payload.paymentId()).isEqualTo(paymentId.value().toString());
        assertThat(payload.merchantId()).isEqualTo(event.merchantId().value().toString());
        assertThat(payload.customerId()).isEqualTo(event.customerId().value().toString());
        assertThat(payload.amountMinorUnits()).isEqualTo(10000);
        assertThat(payload.currencyCode()).isEqualTo("USD");
        assertThat(payload.idempotencyKey()).isEqualTo("idem-key");
        assertThat(payload.authorizationExpiresAt()).isEqualTo(occurredAt.plusSeconds(3600));
        assertThat(payload.occurredAt()).isEqualTo(occurredAt);
        assertThatThrownBy(() -> PaymentInitiatedPayload.from(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("PaymentAuthorized carries authorization code on approval")
    void testPaymentAuthorizedApproved() {
        var event = new PaymentAuthorized(paymentId, Money.of(5000, "USD"), "auth-123", null, occurredAt);

        var payload = PaymentAuthorizedPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_CHARGED);
        assertThat(payload.eventType()).isEqualTo("PaymentAuthorized");
        assertThat(payload.authorizationCode()).isEqualTo("auth-123");
        assertThat(payload.failureCode()).isNull();
        assertThat(payload.failureMessage()).isNull();
        assertThat(payload.authorizedAmountMinorUnits()).isEqualTo(5000);
        assertThat(payload.currencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("PaymentAuthorized carries failure details on decline")
    void testPaymentAuthorizedDeclined() {
        var reason = new FailureReason("insufficient_funds", "card declined");
        var event = new PaymentAuthorized(paymentId, Money.of(5000, "USD"), null, reason, occurredAt);

        var payload = PaymentAuthorizedPayload.from(event);

        assertThat(payload.authorizationCode()).isNull();
        assertThat(payload.failureCode()).isEqualTo("insufficient_funds");
        assertThat(payload.failureMessage()).isEqualTo("card declined");
    }

    @Test
    @DisplayName("PaymentCharged maps onto payment-charged")
    void testPaymentCharged() {
        var captureId = CaptureId.newId();
        var event = new PaymentCharged(paymentId, captureId, Money.of(2500, "EUR"), true, occurredAt);

        var payload = PaymentChargedPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_CHARGED);
        assertThat(payload.eventType()).isEqualTo("PaymentCharged");
        assertThat(payload.captureId()).isEqualTo(captureId.value().toString());
        assertThat(payload.amountMinorUnits()).isEqualTo(2500);
        assertThat(payload.currencyCode()).isEqualTo("EUR");
        assertThat(payload.isFinal()).isTrue();
    }

    @Test
    @DisplayName("CaptureSucceeded maps onto payment-charged")
    void testCaptureSucceeded() {
        var captureId = CaptureId.newId();
        var event = new CaptureSucceeded(paymentId, captureId, "provider-ref-1", occurredAt);

        var payload = CaptureSucceededPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_CHARGED);
        assertThat(payload.eventType()).isEqualTo("CaptureSucceeded");
        assertThat(payload.captureId()).isEqualTo(captureId.value().toString());
        assertThat(payload.providerReference()).isEqualTo("provider-ref-1");
    }

    @Test
    @DisplayName("CaptureFailed maps onto payment-charged")
    void testCaptureFailed() {
        var captureId = CaptureId.newId();
        var reason = new FailureReason("provider_timeout", "capture timed out");
        var event = new CaptureFailed(paymentId, captureId, reason, occurredAt);

        var payload = CaptureFailedPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_CHARGED);
        assertThat(payload.eventType()).isEqualTo("CaptureFailed");
        assertThat(payload.failureCode()).isEqualTo("provider_timeout");
        assertThat(payload.failureMessage()).isEqualTo("capture timed out");
    }

    @Test
    @DisplayName("PaymentCompleted maps onto payment-completed")
    void testPaymentCompleted() {
        var event = new PaymentCompleted(paymentId, occurredAt);

        var payload = PaymentCompletedPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_COMPLETED);
        assertThat(payload.eventType()).isEqualTo("PaymentCompleted");
        assertThat(payload.paymentId()).isEqualTo(paymentId.value().toString());
    }

    @Test
    @DisplayName("PaymentFailed maps onto payment-failed")
    void testPaymentFailed() {
        var reason = new FailureReason("fraud_suspected", "flagged by risk engine");
        var event = new PaymentFailed(paymentId, reason, occurredAt);

        var payload = PaymentFailedPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_FAILED);
        assertThat(payload.eventType()).isEqualTo("PaymentFailed");
        assertThat(payload.failureCode()).isEqualTo("fraud_suspected");
        assertThat(payload.failureMessage()).isEqualTo("flagged by risk engine");
    }

    @Test
    @DisplayName("PaymentExpired also maps onto payment-failed")
    void testPaymentExpired() {
        var event = new PaymentExpired(paymentId, occurredAt);

        var payload = PaymentExpiredPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_FAILED);
        assertThat(payload.eventType()).isEqualTo("PaymentExpired");
    }

    @Test
    @DisplayName("RefundRequested with a capture id maps onto payment-refunded")
    void testRefundRequestedWithCapture() {
        var refundId = RefundId.newId();
        var captureId = CaptureId.newId();
        var reason = RefundReason.of(RefundReasonCode.REQUESTED_BY_CUSTOMER);
        var initiator = new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "merchant-user-1");
        var event = new RefundRequested(
                paymentId, refundId, Money.of(1500, "USD"), captureId, reason, "refund-idem-1", initiator, occurredAt);

        var payload = RefundRequestedPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_REFUNDED);
        assertThat(payload.eventType()).isEqualTo("RefundRequested");
        assertThat(payload.refundId()).isEqualTo(refundId.value().toString());
        assertThat(payload.captureId()).isEqualTo(captureId.value().toString());
        assertThat(payload.amountMinorUnits()).isEqualTo(1500);
        assertThat(payload.reasonCode()).isEqualTo("REQUESTED_BY_CUSTOMER");
        assertThat(payload.idempotencyKey()).isEqualTo("refund-idem-1");
        assertThat(payload.initiatedByType()).isEqualTo("MERCHANT_USER");
        assertThat(payload.initiatedById()).isEqualTo("merchant-user-1");
    }

    @Test
    @DisplayName("RefundRequested without a capture id leaves captureId null")
    void testRefundRequestedWithoutCapture() {
        var refundId = RefundId.newId();
        var reason = new RefundReason(RefundReasonCode.DUPLICATE, "duplicate charge noticed by customer");
        var initiator = new RefundInitiator(RefundInitiatorType.PLATFORM_OPERATOR, "ops-1");
        var event = new RefundRequested(
                paymentId, refundId, Money.of(1500, "USD"), null, reason, "refund-idem-2", initiator, occurredAt);

        var payload = RefundRequestedPayload.from(event);

        assertThat(payload.captureId()).isNull();
    }

    @Test
    @DisplayName("RefundRequested's free-text reason notes never reach the wire payload")
    void testRefundRequestedPayloadNeverExposesReasonNotes() {
        var componentNames = Arrays.stream(RefundRequestedPayload.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames).doesNotContain("reasonNotes", "notes");
    }

    @Test
    @DisplayName("RefundPending maps onto payment-refunded")
    void testRefundPending() {
        var refundId = RefundId.newId();
        var event = new RefundPending(paymentId, refundId, occurredAt);

        var payload = RefundPendingPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_REFUNDED);
        assertThat(payload.eventType()).isEqualTo("RefundPending");
        assertThat(payload.refundId()).isEqualTo(refundId.value().toString());
    }

    @Test
    @DisplayName("RefundSucceeded maps onto payment-refunded")
    void testRefundSucceeded() {
        var refundId = RefundId.newId();
        var event = new RefundSucceeded(paymentId, refundId, "provider-refund-ref", occurredAt);

        var payload = RefundSucceededPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_REFUNDED);
        assertThat(payload.eventType()).isEqualTo("RefundSucceeded");
        assertThat(payload.providerReference()).isEqualTo("provider-refund-ref");
    }

    @Test
    @DisplayName("RefundFailed maps onto payment-refunded")
    void testRefundFailed() {
        var refundId = RefundId.newId();
        var reason = new FailureReason("provider_rejected", "refund rejected by provider");
        var event = new RefundFailed(paymentId, refundId, reason, occurredAt);

        var payload = RefundFailedPayload.from(event);

        assertThat(payload.topic()).isEqualTo(PaymentKafkaTopics.PAYMENT_REFUNDED);
        assertThat(payload.eventType()).isEqualTo("RefundFailed");
        assertThat(payload.failureCode()).isEqualTo("provider_rejected");
        assertThat(payload.failureMessage()).isEqualTo("refund rejected by provider");
    }

    @Test
    @DisplayName("payload records reject null required fields")
    void testNullValidation() {
        assertThatThrownBy(() -> new PaymentCompletedPayload(null, occurredAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentCompletedPayload(paymentId.value().toString(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
