package com.orvigas.payment.publish;

import com.orvigas.payment.PaymentExpired;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link PaymentExpired}, published to
 * {@value PaymentKafkaTopics#PAYMENT_FAILED} - an expired authorization is a
 * terminal failure outcome with no provider decline, and consumers of the
 * failure topic already need to treat it the same as a declined payment.
 *
 * @param paymentId the payment identifier
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentExpiredPayload(String paymentId, Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "PaymentExpired";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public PaymentExpiredPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static PaymentExpiredPayload from(PaymentExpired event) {
        Objects.requireNonNull(event, "event must not be null");
        return new PaymentExpiredPayload(event.paymentId().value().toString(), event.occurredAt());
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
