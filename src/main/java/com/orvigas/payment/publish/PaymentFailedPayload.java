package com.orvigas.payment.publish;

import com.orvigas.payment.PaymentFailed;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link PaymentFailed}, published to
 * {@value PaymentKafkaTopics#PAYMENT_FAILED}.
 *
 * @param paymentId the payment identifier
 * @param failureCode machine-readable failure code
 * @param failureMessage human-readable failure explanation
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentFailedPayload(
        String paymentId,
        String failureCode,
        String failureMessage,
        Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "PaymentFailed";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public PaymentFailedPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(failureCode, "failureCode must not be null");
        Objects.requireNonNull(failureMessage, "failureMessage must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static PaymentFailedPayload from(PaymentFailed event) {
        Objects.requireNonNull(event, "event must not be null");
        return new PaymentFailedPayload(
                event.paymentId().value().toString(),
                event.reason().code(),
                event.reason().message(),
                event.occurredAt());
    }

    @Override
    public String topic() {
        return PaymentKafkaTopics.PAYMENT_FAILED;
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
