package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a payment fails. Maps to the Kafka topic
 * {@code payment-failed}.
 *
 * @param paymentId the aggregate identifier
 * @param reason failure details
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentFailed(PaymentId paymentId, FailureReason reason, Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public PaymentFailed {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
