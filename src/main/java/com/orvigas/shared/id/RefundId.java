package com.orvigas.shared.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a Refund entity inside the Payment aggregate. Referenced by
 * settlement entries and provider reconciliation — see
 * {@code knowledge/domain/refund.md}.
 *
 * @param value the underlying identifier
 * @author orvigas@gmail.com
 */
public record RefundId(UUID value) implements DomainId {

    /**
     * Validates the wrapped value.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public RefundId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Generates a new, random refund identifier.
     *
     * @return a fresh {@code RefundId}
     */
    public static RefundId newId() {
        return new RefundId(UUID.randomUUID());
    }

    /**
     * Wraps an existing UUID, for example when rehydrating from an event or
     * a read-side projection.
     *
     * @param value the UUID to wrap
     * @return the wrapping {@code RefundId}
     */
    public static RefundId of(UUID value) {
        return new RefundId(value);
    }

    /**
     * Parses a canonical UUID string into a refund identifier.
     *
     * @param value canonical UUID string representation
     * @return the parsed {@code RefundId}
     * @throws IllegalArgumentException if {@code value} is not a valid UUID
     */
    public static RefundId fromString(String value) {
        return new RefundId(UUID.fromString(value));
    }
}
