package com.orvigas.payment.publish;

import com.orvigas.payment.CaptureSucceeded;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link CaptureSucceeded}, published to
 * {@value PaymentKafkaTopics#PAYMENT_CHARGED}.
 *
 * @param paymentId the payment identifier
 * @param captureId the capture entity identifier
 * @param providerReference provider's identifier for this capture
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record CaptureSucceededPayload(
        String paymentId,
        String captureId,
        String providerReference,
        Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "CaptureSucceeded";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public CaptureSucceededPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(captureId, "captureId must not be null");
        Objects.requireNonNull(providerReference, "providerReference must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static CaptureSucceededPayload from(CaptureSucceeded event) {
        Objects.requireNonNull(event, "event must not be null");
        return new CaptureSucceededPayload(
                event.paymentId().value().toString(),
                event.captureId().value().toString(),
                event.providerReference(),
                event.occurredAt());
    }

    @Override
    public String topic() {
        return PaymentKafkaTopics.PAYMENT_CHARGED;
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
