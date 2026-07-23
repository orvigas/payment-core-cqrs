package com.orvigas.payment;

/**
 * Structured refund reason codes for analytics and merchant reporting.
 *
 * @author orvigas@gmail.com
 */
public enum RefundReasonCode {
    REQUESTED_BY_CUSTOMER,
    DUPLICATE,
    FRAUD,
    ORDER_CANCELLED
}
