package com.orvigas.payment.publish;

import com.orvigas.payment.PaymentInitiated;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link PaymentInitiated}, published to
 * {@value PaymentKafkaTopics#PAYMENT_INITIATED}.
 *
 * <p>The tokenized payment method is intentionally left off the wire: per
 * {@code governance/SECURITY_POLICY.md}, Kafka topics are long-lived records
 * that are impractical to selectively erase, and the provider token isn't
 * needed by any read-side consumer this event feeds.
 *
 * @param paymentId the payment identifier
 * @param merchantId the merchant receiving funds
 * @param customerId the paying customer
 * @param amountMinorUnits requested amount, in the currency's minor units
 * @param currencyCode ISO 4217 currency code
 * @param idempotencyKey client-supplied key for idempotent retries
 * @param authorizationExpiresAt deadline for capturing the authorization
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentInitiatedPayload(
        String paymentId,
        String merchantId,
        String customerId,
        long amountMinorUnits,
        String currencyCode,
        String idempotencyKey,
        Instant authorizationExpiresAt,
        Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "PaymentInitiated";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public PaymentInitiatedPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(authorizationExpiresAt, "authorizationExpiresAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static PaymentInitiatedPayload from(PaymentInitiated event) {
        Objects.requireNonNull(event, "event must not be null");
        return new PaymentInitiatedPayload(
                event.paymentId().value().toString(),
                event.merchantId().value().toString(),
                event.customerId().value().toString(),
                event.amount().minorUnits(),
                event.amount().currency().getCurrencyCode(),
                event.idempotencyKey(),
                event.authorizationExpiresAt(),
                event.occurredAt());
    }

    @Override
    public String topic() {
        return PaymentKafkaTopics.PAYMENT_INITIATED;
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
