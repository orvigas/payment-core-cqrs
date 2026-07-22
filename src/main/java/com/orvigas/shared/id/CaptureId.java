package com.orvigas.shared.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a Capture entity inside the Payment aggregate. Unique across
 * the platform, not just within the owning payment, because settlement
 * entries and provider reconciliation reference it directly — see
 * {@code knowledge/domain/capture.md}.
 *
 * @param value the underlying identifier
 * @author orvigas@gmail.com
 */
public record CaptureId(UUID value) implements DomainId {

    /**
     * Validates the wrapped value.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public CaptureId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Generates a new, random capture identifier.
     *
     * @return a fresh {@code CaptureId}
     */
    public static CaptureId newId() {
        return new CaptureId(UUID.randomUUID());
    }

    /**
     * Wraps an existing UUID, for example when rehydrating from an event or
     * a read-side projection.
     *
     * @param value the UUID to wrap
     * @return the wrapping {@code CaptureId}
     */
    public static CaptureId of(UUID value) {
        return new CaptureId(value);
    }

    /**
     * Parses a canonical UUID string into a capture identifier.
     *
     * @param value canonical UUID string representation
     * @return the parsed {@code CaptureId}
     * @throws IllegalArgumentException if {@code value} is not a valid UUID
     */
    public static CaptureId fromString(String value) {
        return new CaptureId(UUID.fromString(value));
    }
}
