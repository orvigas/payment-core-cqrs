package com.orvigas.payment.publish;

import com.orvigas.payment.PaymentCompleted;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link PaymentCompleted}, published to
 * {@value PaymentKafkaTopics#PAYMENT_COMPLETED}.
 *
 * @param paymentId the payment identifier
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentCompletedPayload(String paymentId, Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "PaymentCompleted";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public PaymentCompletedPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static PaymentCompletedPayload from(PaymentCompleted event) {
        Objects.requireNonNull(event, "event must not be null");
        return new PaymentCompletedPayload(event.paymentId().value().toString(), event.occurredAt());
    }

    @Override
    public String topic() {
        return PaymentKafkaTopics.PAYMENT_COMPLETED;
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
