package com.orvigas.shared.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a Settlement aggregate, keyed by merchant and period. Appears
 * on merchant reports and bank transfer references — see
 * {@code knowledge/domain/settlement.md}.
 *
 * @param value the underlying identifier
 * @author orvigas@gmail.com
 */
public record SettlementId(UUID value) implements DomainId {

    /**
     * Validates the wrapped value.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public SettlementId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Generates a new, random settlement identifier.
     *
     * @return a fresh {@code SettlementId}
     */
    public static SettlementId newId() {
        return new SettlementId(UUID.randomUUID());
    }

    /**
     * Wraps an existing UUID, for example when rehydrating from an event or
     * a read-side projection.
     *
     * @param value the UUID to wrap
     * @return the wrapping {@code SettlementId}
     */
    public static SettlementId of(UUID value) {
        return new SettlementId(value);
    }

    /**
     * Parses a canonical UUID string into a settlement identifier.
     *
     * @param value canonical UUID string representation
     * @return the parsed {@code SettlementId}
     * @throws IllegalArgumentException if {@code value} is not a valid UUID
     */
    public static SettlementId fromString(String value) {
        return new SettlementId(UUID.fromString(value));
    }
}
