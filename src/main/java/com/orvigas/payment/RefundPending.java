package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import java.time.Instant;
import java.util.Objects;

/**
 * Internal event: records that a refund has moved to pending while the provider
 * processes it.
 *
 * @param paymentId the aggregate identifier
 * @param refundId the refund entity identifier
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record RefundPending(
        PaymentId paymentId,
        RefundId refundId,
        Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public RefundPending {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
