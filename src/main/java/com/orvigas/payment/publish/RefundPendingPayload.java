package com.orvigas.payment.publish;

import com.orvigas.payment.RefundPending;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link RefundPending}, published to
 * {@value PaymentKafkaTopics#PAYMENT_REFUNDED}.
 *
 * @param paymentId the payment identifier
 * @param refundId the refund entity identifier
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record RefundPendingPayload(String paymentId, String refundId, Instant occurredAt)
        implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "RefundPending";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public RefundPendingPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static RefundPendingPayload from(RefundPending event) {
        Objects.requireNonNull(event, "event must not be null");
        return new RefundPendingPayload(
                event.paymentId().value().toString(),
                event.refundId().value().toString(),
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
