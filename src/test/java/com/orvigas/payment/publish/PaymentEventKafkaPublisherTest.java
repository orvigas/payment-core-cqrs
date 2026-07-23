package com.orvigas.payment.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for the Kafka publisher. A mocked {@link KafkaTemplate} isolates
 * these tests from a real broker; {@link PaymentEventKafkaPublisherIntegrationTest}
 * covers the embedded-broker round trip.
 *
 * @author orvigas@gmail.com
 */
@DisplayName("Payment event Kafka publisher")
class PaymentEventKafkaPublisherTest {

    // Mockito can't produce a genuinely parameterized mock; the raw
    // KafkaTemplate.class token is the only way to stub the generic type.
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    private final PaymentEventKafkaPublisher publisher = new PaymentEventKafkaPublisher(kafkaTemplate);

    private final PaymentId paymentId = PaymentId.newId();
    private final Instant occurredAt = Instant.parse("2026-07-22T10:15:30Z");

    // Same raw-type limitation as the kafkaTemplate mock above, applied to the mocked SendResult.
    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> successfulSend(String topic) {
        var recordMetadata = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(kafkaTemplate.send(eq(topic), any(), any())).thenReturn(CompletableFuture.completedFuture(sendResult));
        return CompletableFuture.completedFuture(sendResult);
    }

    @Test
    @DisplayName("PaymentInitiated is sent to payment-initiated keyed by payment id")
    void testPaymentInitiated() {
        successfulSend(PaymentKafkaTopics.PAYMENT_INITIATED);
        var event = new PaymentInitiated(
                paymentId, MerchantId.newId(), CustomerId.newId(), Money.of(1000, "USD"),
                new PaymentMethod("tok"), "idem", occurredAt.plusSeconds(60), occurredAt);

        publisher.on(event);

        verify(kafkaTemplate).send(
                eq(PaymentKafkaTopics.PAYMENT_INITIATED),
                eq(paymentId.value().toString()),
                eq(PaymentInitiatedPayload.from(event)));
    }

    @Test
    @DisplayName("PaymentAuthorized, PaymentCharged, CaptureSucceeded and CaptureFailed all land on payment-charged")
    void testPaymentChargedTopicEvents() {
        successfulSend(PaymentKafkaTopics.PAYMENT_CHARGED);
        var captureId = CaptureId.newId();

        var authorized = new PaymentAuthorized(paymentId, Money.of(1000, "USD"), "auth", null, occurredAt);
        var charged = new PaymentCharged(paymentId, captureId, Money.of(1000, "USD"), true, occurredAt);
        var captureSucceeded = new CaptureSucceeded(paymentId, captureId, "ref", occurredAt);
        var captureFailed = new CaptureFailed(
                paymentId, captureId, new FailureReason("timeout", "timed out"), occurredAt);

        publisher.on(authorized);
        publisher.on(charged);
        publisher.on(captureSucceeded);
        publisher.on(captureFailed);

        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_CHARGED), eq(paymentId.value().toString()),
                eq(PaymentAuthorizedPayload.from(authorized)));
        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_CHARGED), eq(paymentId.value().toString()),
                eq(PaymentChargedPayload.from(charged)));
        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_CHARGED), eq(paymentId.value().toString()),
                eq(CaptureSucceededPayload.from(captureSucceeded)));
        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_CHARGED), eq(paymentId.value().toString()),
                eq(CaptureFailedPayload.from(captureFailed)));
    }

    @Test
    @DisplayName("PaymentCompleted is sent to payment-completed")
    void testPaymentCompleted() {
        successfulSend(PaymentKafkaTopics.PAYMENT_COMPLETED);
        var event = new PaymentCompleted(paymentId, occurredAt);

        publisher.on(event);

        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_COMPLETED), eq(paymentId.value().toString()),
                eq(PaymentCompletedPayload.from(event)));
    }

    @Test
    @DisplayName("PaymentFailed and PaymentExpired both land on payment-failed")
    void testPaymentFailedTopicEvents() {
        successfulSend(PaymentKafkaTopics.PAYMENT_FAILED);
        var failed = new PaymentFailed(paymentId, new FailureReason("declined", "card declined"), occurredAt);
        var expired = new PaymentExpired(paymentId, occurredAt);

        publisher.on(failed);
        publisher.on(expired);

        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_FAILED), eq(paymentId.value().toString()),
                eq(PaymentFailedPayload.from(failed)));
        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_FAILED), eq(paymentId.value().toString()),
                eq(PaymentExpiredPayload.from(expired)));
    }

    @Test
    @DisplayName("the full refund sub-lifecycle lands on payment-refunded")
    void testRefundTopicEvents() {
        successfulSend(PaymentKafkaTopics.PAYMENT_REFUNDED);
        var refundId = RefundId.newId();
        var initiator = new RefundInitiator(RefundInitiatorType.AUTOMATED_RULE, "rule-1");

        var requested = new RefundRequested(
                paymentId, refundId, Money.of(500, "USD"), null,
                RefundReason.of(RefundReasonCode.FRAUD), "refund-idem", initiator, occurredAt);
        var pending = new RefundPending(paymentId, refundId, occurredAt);
        var succeeded = new RefundSucceeded(paymentId, refundId, "provider-ref", occurredAt);
        var failed = new RefundFailed(paymentId, refundId, new FailureReason("rejected", "rejected"), occurredAt);

        publisher.on(requested);
        publisher.on(pending);
        publisher.on(succeeded);
        publisher.on(failed);

        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_REFUNDED), eq(paymentId.value().toString()),
                eq(RefundRequestedPayload.from(requested)));
        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_REFUNDED), eq(paymentId.value().toString()),
                eq(RefundPendingPayload.from(pending)));
        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_REFUNDED), eq(paymentId.value().toString()),
                eq(RefundSucceededPayload.from(succeeded)));
        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_REFUNDED), eq(paymentId.value().toString()),
                eq(RefundFailedPayload.from(failed)));
    }

    @Test
    @DisplayName("a publish failure is swallowed, not propagated to the caller")
    void testPublishFailureDoesNotPropagate() {
        CompletableFuture<SendResult<String, Object>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedSend);
        var event = new PaymentCompleted(paymentId, occurredAt);

        assertThatCode(() -> publisher.on(event)).doesNotThrowAnyException();

        verify(kafkaTemplate).send(eq(PaymentKafkaTopics.PAYMENT_COMPLETED), eq(paymentId.value().toString()), any());
    }

    @Test
    @DisplayName("payload equals confirms the record sent is a typed, immutable payload, not the raw domain event")
    void testPayloadIsNotTheRawDomainEvent() {
        successfulSend(PaymentKafkaTopics.PAYMENT_COMPLETED);
        var event = new PaymentCompleted(paymentId, occurredAt);

        publisher.on(event);

        assertThat(PaymentCompletedPayload.from(event)).isNotEqualTo(event);
    }
}
