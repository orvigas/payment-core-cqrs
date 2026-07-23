package com.orvigas.merchant;

import java.util.Objects;

/**
 * Structured reason for a merchant suspension.
 *
 * @param code        high-level category of the suspension
 * @param description free-text explanation (logged but not user-visible)
 * @author orvigas@gmail.com
 */
public record SuspensionReason(SuspensionReasonCode code, String description) {

    public SuspensionReason {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }
}
