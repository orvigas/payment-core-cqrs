package com.orvigas.payment.publish;

import com.orvigas.payment.PaymentCharged;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link PaymentCharged}, published to
 * {@value PaymentKafkaTopics#PAYMENT_CHARGED}.
 *
 * @param paymentId the payment identifier
 * @param captureId the capture entity identifier
 * @param amountMinorUnits amount being captured, in the currency's minor units
 * @param currencyCode ISO 4217 currency code
 * @param isFinal whether this is the final capture for the authorization
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentChargedPayload(
        String paymentId,
        String captureId,
        long amountMinorUnits,
        String currencyCode,
        boolean isFinal,
        Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "PaymentCharged";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if a required field is null
     */
    public PaymentChargedPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(captureId, "captureId must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static PaymentChargedPayload from(PaymentCharged event) {
        Objects.requireNonNull(event, "event must not be null");
        return new PaymentChargedPayload(
                event.paymentId().value().toString(),
                event.captureId().value().toString(),
                event.amount().minorUnits(),
                event.amount().currency().getCurrencyCode(),
                event.isFinal(),
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
