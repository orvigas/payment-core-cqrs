package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.PaymentId;
import java.time.Instant;
import java.util.Objects;

/**
 * Internal event: recorded when a capture's provider confirmation is processed.
 *
 * @param paymentId the aggregate identifier
 * @param captureId the capture entity identifier
 * @param providerReference provider's identifier for this capture
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record CaptureSucceeded(
        PaymentId paymentId,
        CaptureId captureId,
        String providerReference,
        Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public CaptureSucceeded {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(captureId, "captureId must not be null");
        Objects.requireNonNull(providerReference, "providerReference must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
