package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a capture is requested. Maps to the Kafka topic
 * {@code payment-charged}.
 *
 * @param paymentId the aggregate identifier
 * @param captureId the new capture entity identifier
 * @param amount amount being captured
 * @param isFinal whether this is the final capture for this authorization
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentCharged(
        PaymentId paymentId,
        CaptureId captureId,
        Money amount,
        boolean isFinal,
        Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any required field is null
     */
    public PaymentCharged {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(captureId, "captureId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
