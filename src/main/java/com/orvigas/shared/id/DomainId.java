package com.orvigas.shared.id;

import java.util.UUID;

/**
 * Marker for the typed identifier wrappers in this package. Aggregates and
 * entities reference each other through these types, never through raw
 * {@link UUID}, so a method signature alone tells the reader which entity an
 * id points to.
 *
 * @author orvigas@gmail.com
 */
public interface DomainId {

    /**
     * Returns the wrapped UUID value.
     *
     * @return the underlying identifier
     */
    UUID value();
}
