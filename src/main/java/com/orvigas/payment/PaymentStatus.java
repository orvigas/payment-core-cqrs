package com.orvigas.payment;

/**
 * Lifecycle state of a Payment aggregate.
 *
 * @author orvigas@gmail.com
 */
public enum PaymentStatus {
    INITIATED,
    AUTHORIZED,
    PARTIALLY_CAPTURED,
    CAPTURED,
    COMPLETED,
    FAILED,
    EXPIRED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
