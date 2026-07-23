package com.orvigas.shared.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a Customer. Referenced from payments for authorization checks
 * and risk rules, never loaded across the aggregate boundary.
 *
 * @param value the underlying identifier
 * @author orvigas@gmail.com
 */
public record CustomerId(UUID value) implements DomainId {

    /**
     * Validates the wrapped value.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public CustomerId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Generates a new, random customer identifier.
     *
     * @return a fresh {@code CustomerId}
     */
    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID());
    }

    /**
     * Wraps an existing UUID, for example when rehydrating from an event or
     * a read-side projection.
     *
     * @param value the UUID to wrap
     * @return the wrapping {@code CustomerId}
     */
    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    /**
     * Parses a canonical UUID string into a customer identifier.
     *
     * @param value canonical UUID string representation
     * @return the parsed {@code CustomerId}
     * @throws IllegalArgumentException if {@code value} is not a valid UUID
     */
    public static CustomerId fromString(String value) {
        return new CustomerId(UUID.fromString(value));
    }
}
