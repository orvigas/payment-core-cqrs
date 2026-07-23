package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.PaymentId;
import java.time.Instant;
import java.util.Objects;

/**
 * Internal event: recorded when a capture's provider decline is processed.
 *
 * @param paymentId the aggregate identifier
 * @param captureId the capture entity identifier
 * @param reason failure details
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record CaptureFailed(
        PaymentId paymentId,
        CaptureId captureId,
        FailureReason reason,
        Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public CaptureFailed {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(captureId, "captureId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
