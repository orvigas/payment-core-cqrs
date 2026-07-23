package com.orvigas.payment;

/**
 * Identifies who triggered a refund for audit and analytics.
 *
 * @author orvigas@gmail.com
 */
public enum RefundInitiatorType {
    MERCHANT_USER,
    PLATFORM_OPERATOR,
    AUTOMATED_RULE
}
