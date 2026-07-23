package com.orvigas.payment.api;

import java.util.UUID;

/**
 * Response returned after a capture is requested.
 *
 * @param paymentId the payment identifier
 * @param captureId the new capture entity identifier
 * @param status    the payment status
 * @param amount    the captured amount
 * @author orvigas@gmail.com
 */
public record CapturePaymentResponse(
        UUID paymentId,
        UUID captureId,
        String status,
        MoneyResponse amount) {
}
