package com.orvigas.payment;

import java.util.Objects;

/**
 * Structured reason for a refund, plus optional free-text note.
 *
 * @param code structured reason code
 * @param notes optional free-text explanation
 * @author orvigas@gmail.com
 */
public record RefundReason(RefundReasonCode code, String notes) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if {@code code} is null
     */
    public RefundReason {
        Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * Creates a refund reason with the given code and no notes.
     *
     * @param code the reason code
     * @return a refund reason
     */
    public static RefundReason of(RefundReasonCode code) {
        return new RefundReason(code, null);
    }
}
