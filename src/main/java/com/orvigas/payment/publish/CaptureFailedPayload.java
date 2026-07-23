package com.orvigas.payment.publish;

import com.orvigas.payment.CaptureFailed;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link CaptureFailed}, published to
 * {@value PaymentKafkaTopics#PAYMENT_CHARGED}.
 *
 * @param paymentId the payment identifier
 * @param captureId the capture entity identifier
 * @param failureCode machine-readable failure code
 * @param failureMessage human-readable failure explanation
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record CaptureFailedPayload(
        String paymentId,
        String captureId,
        String failureCode,
        String failureMessage,
        Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "CaptureFailed";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public CaptureFailedPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(captureId, "captureId must not be null");
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
    public static CaptureFailedPayload from(CaptureFailed event) {
        Objects.requireNonNull(event, "event must not be null");
        return new CaptureFailedPayload(
                event.paymentId().value().toString(),
                event.captureId().value().toString(),
                event.reason().code(),
                event.reason().message(),
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
