package com.orvigas.payment.publish;

import com.orvigas.payment.RefundRequested;
import java.time.Instant;
import java.util.Objects;

/**
 * Kafka payload for {@link RefundRequested}, published to
 * {@value PaymentKafkaTopics#PAYMENT_REFUNDED}.
 *
 * @param paymentId the payment identifier
 * @param refundId the refund entity identifier
 * @param amountMinorUnits amount being refunded, in the currency's minor units
 * @param currencyCode ISO 4217 currency code
 * @param captureId the capture being refunded, or null when the provider doesn't require one
 * @param reasonCode structured refund reason code
 * @param reasonNotes optional free-text explanation, or null
 * @param idempotencyKey client-supplied key for idempotent retries
 * @param initiatedByType the kind of actor that initiated the refund
 * @param initiatedById the actor's identifier within its own system
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record RefundRequestedPayload(
        String paymentId,
        String refundId,
        long amountMinorUnits,
        String currencyCode,
        String captureId,
        String reasonCode,
        String reasonNotes,
        String idempotencyKey,
        String initiatedByType,
        String initiatedById,
        Instant occurredAt) implements PaymentKafkaEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "RefundRequested";

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if a required field is null
     */
    public RefundRequestedPayload {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(initiatedByType, "initiatedByType must not be null");
        Objects.requireNonNull(initiatedById, "initiatedById must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Builds the wire payload from the domain event.
     *
     * @param event the domain event
     * @return the Kafka payload
     */
    public static RefundRequestedPayload from(RefundRequested event) {
        Objects.requireNonNull(event, "event must not be null");
        String captureId = event.captureId() != null ? event.captureId().value().toString() : null;
        return new RefundRequestedPayload(
                event.paymentId().value().toString(),
                event.refundId().value().toString(),
                event.amount().minorUnits(),
                event.amount().currency().getCurrencyCode(),
                captureId,
                event.reason().code().name(),
                event.reason().notes(),
                event.idempotencyKey(),
                event.initiatedBy().type().name(),
                event.initiatedBy().id(),
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
