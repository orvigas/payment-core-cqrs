package com.orvigas.payment;

import java.util.Objects;

/**
 * Identifies who triggered a refund for audit and analytics.
 *
 * @param type the kind of actor that initiated the refund
 * @param id the actor's identifier within its own system
 * @author orvigas@gmail.com
 */
public record RefundInitiator(RefundInitiatorType type, String id) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public RefundInitiator {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }
}
