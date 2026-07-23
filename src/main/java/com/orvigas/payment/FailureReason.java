package com.orvigas.payment;

import java.util.Objects;

/**
 * Machine-readable failure code and human-readable message when a payment fails.
 *
 * @param code machine-readable code that distinguishes retryable from terminal failures
 * @param message human-readable explanation of the failure
 * @author orvigas@gmail.com
 */
public record FailureReason(String code, String message) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public FailureReason {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
