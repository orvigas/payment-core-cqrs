package com.orvigas.payment;

import java.util.Objects;

/**
 * Machine-readable failure code and human-readable message when a payment fails.
 *
 * <p>{@code message} is published verbatim on the {@code payment-charged},
 * {@code payment-failed}, and {@code payment-refunded} Kafka topics (see
 * {@code com.orvigas.payment.publish}). Whoever constructs one of these from
 * a real payment provider's response must curate this into a stable,
 * provider-agnostic string rather than passing the provider's raw response
 * text through - that text is out of this application's control and isn't
 * something we want permanently retained and replayable.
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
