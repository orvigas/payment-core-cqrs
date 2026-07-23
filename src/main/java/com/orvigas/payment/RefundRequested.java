package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a refund is requested. Maps to the Kafka topic
 * implicitly as part of the payment's refund flow.
 *
 * @param paymentId the aggregate identifier
 * @param refundId the new refund entity identifier
 * @param amount amount being refunded
 * @param reason structured reason plus optional notes
 * @param idempotencyKey client-supplied key for idempotent retries
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record RefundRequested(
        PaymentId paymentId,
        RefundId refundId,
        Money amount,
        RefundReason reason,
        String idempotencyKey,
        Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public RefundRequested {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
