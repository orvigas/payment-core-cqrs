package com.orvigas.payment.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Response returned after a payment is initiated.
 *
 * @param paymentId the new payment aggregate identifier
 * @param status    the payment status after initiation
 * @param createdAt when the payment was initiated
 * @author orvigas@gmail.com
 */
public record InitiatePaymentResponse(
        UUID paymentId,
        String status,
        Instant createdAt) {
}
