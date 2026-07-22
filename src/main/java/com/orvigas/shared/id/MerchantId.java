package com.orvigas.shared.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a Merchant aggregate. Referenced from payments and
 * settlements by id, never loaded across the aggregate boundary — see
 * {@code knowledge/domain/merchant.md}.
 *
 * @param value the underlying identifier
 * @author orvigas@gmail.com
 */
public record MerchantId(UUID value) implements DomainId {

    /**
     * Validates the wrapped value.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public MerchantId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Generates a new, random merchant identifier.
     *
     * @return a fresh {@code MerchantId}
     */
    public static MerchantId newId() {
        return new MerchantId(UUID.randomUUID());
    }

    /**
     * Wraps an existing UUID, for example when rehydrating from an event or
     * a read-side projection.
     *
     * @param value the UUID to wrap
     * @return the wrapping {@code MerchantId}
     */
    public static MerchantId of(UUID value) {
        return new MerchantId(value);
    }

    /**
     * Parses a canonical UUID string into a merchant identifier.
     *
     * @param value canonical UUID string representation
     * @return the parsed {@code MerchantId}
     * @throws IllegalArgumentException if {@code value} is not a valid UUID
     */
    public static MerchantId fromString(String value) {
        return new MerchantId(UUID.fromString(value));
    }
}
