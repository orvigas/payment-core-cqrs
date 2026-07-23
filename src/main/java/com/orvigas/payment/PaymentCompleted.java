package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a payment is completed. Maps to the Kafka topic
 * {@code payment-completed}.
 *
 * @param paymentId the aggregate identifier
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentCompleted(PaymentId paymentId, Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public PaymentCompleted {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
