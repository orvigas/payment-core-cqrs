package com.orvigas.payment;

/**
 * Lifecycle state of a Refund entity.
 *
 * @author orvigas@gmail.com
 */
public enum RefundStatus {
    REQUESTED,
    PENDING,
    SUCCEEDED,
    FAILED
}
