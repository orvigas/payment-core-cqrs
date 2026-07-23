package com.orvigas.payment.publish;

import com.orvigas.payment.PaymentAuthorized;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link PaymentAuthorized}, published to
 * {@value PaymentKafkaTopics#PAYMENT_CHARGED}.
 *
 * @param paymentId the payment identifier
 * @param authorizedAmountMinorUnits amount the provider approved, or the requested amount on decline
 * @param currencyCode ISO 4217 currency code
 * @param authorizationCode provider's authorization reference, null on decline
 * @param failureCode provider decline code, null on approval
 * @param failureMessage provider decline message, null on approval
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentAuthorizedPayload(
        String paymentId,
        long authorizedAmountMinorUnits,
        String currencyCode,
        String authorizationCode,
        String failureCode,
        String failureMessage,
        Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "PaymentAuthorized";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if a required field is null
     */
    public PaymentAuthorizedPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static PaymentAuthorizedPayload from(PaymentAuthorized event) {
        Objects.requireNonNull(event, "event must not be null");
        String failureCode = event.failureReason() != null ? event.failureReason().code() : null;
        String failureMessage = event.failureReason() != null ? event.failureReason().message() : null;
        return new PaymentAuthorizedPayload(
                event.paymentId().value().toString(),
                event.authorizedAmount().minorUnits(),
                event.authorizedAmount().currency().getCurrencyCode(),
                event.authorizationCode(),
                failureCode,
                failureMessage,
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
