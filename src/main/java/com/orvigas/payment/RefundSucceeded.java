package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a refund succeeds.
 *
 * @param paymentId the aggregate identifier
 * @param refundId the refund entity identifier
 * @param providerReference provider's identifier for this refund
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record RefundSucceeded(
        PaymentId paymentId,
        RefundId refundId,
        String providerReference,
        Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public RefundSucceeded {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(providerReference, "providerReference must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
