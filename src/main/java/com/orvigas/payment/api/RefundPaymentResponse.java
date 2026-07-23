package com.orvigas.payment.api;

import java.util.UUID;

/**
 * Response returned after a refund is requested.
 *
 * @param paymentId the payment identifier
 * @param refundId  the new refund entity identifier
 * @param status    the payment status
 * @param amount    the refunded amount
 * @author orvigas@gmail.com
 */
public record RefundPaymentResponse(
        UUID paymentId,
        UUID refundId,
        String status,
        MoneyResponse amount) {
}
