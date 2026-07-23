package com.orvigas.payment.publish;

import com.orvigas.payment.RefundSucceeded;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link RefundSucceeded}, published to
 * {@value PaymentKafkaTopics#PAYMENT_REFUNDED}.
 *
 * @param paymentId the payment identifier
 * @param refundId the refund entity identifier
 * @param providerReference provider's identifier for this refund
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record RefundSucceededPayload(
        String paymentId,
        String refundId,
        String providerReference,
        Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "RefundSucceeded";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public RefundSucceededPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(providerReference, "providerReference must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static RefundSucceededPayload from(RefundSucceeded event) {
        Objects.requireNonNull(event, "event must not be null");
        return new RefundSucceededPayload(
                event.paymentId().value().toString(),
                event.refundId().value().toString(),
                event.providerReference(),
                event.occurredAt());
    }

    @Override
    public String topic() {
        return PaymentKafkaTopics.PAYMENT_REFUNDED;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }
}
