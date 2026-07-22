package com.orvigas.shared.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a Payment aggregate. Application-assigned at initiation, so
 * new-vs-existing must be modeled explicitly rather than inferred from a
 * null id — see {@code knowledge/domain/payment.md}.
 *
 * @param value the underlying identifier
 * @author orvigas@gmail.com
 */
public record PaymentId(UUID value) implements DomainId {

    /**
     * Validates the wrapped value.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public PaymentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Generates a new, random payment identifier.
     *
     * @return a fresh {@code PaymentId}
     */
    public static PaymentId newId() {
        return new PaymentId(UUID.randomUUID());
    }

    /**
     * Wraps an existing UUID, for example when rehydrating from an event or
     * a read-side projection.
     *
     * @param value the UUID to wrap
     * @return the wrapping {@code PaymentId}
     */
    public static PaymentId of(UUID value) {
        return new PaymentId(value);
    }

    /**
     * Parses a canonical UUID string into a payment identifier.
     *
     * @param value canonical UUID string representation
     * @return the parsed {@code PaymentId}
     * @throws IllegalArgumentException if {@code value} is not a valid UUID
     */
    public static PaymentId fromString(String value) {
        return new PaymentId(UUID.fromString(value));
    }
}
