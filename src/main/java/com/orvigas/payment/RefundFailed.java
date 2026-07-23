package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a refund fails.
 *
 * @param paymentId the aggregate identifier
 * @param refundId the refund entity identifier
 * @param reason failure details
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record RefundFailed(
        PaymentId paymentId,
        RefundId refundId,
        FailureReason reason,
        Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public RefundFailed {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
