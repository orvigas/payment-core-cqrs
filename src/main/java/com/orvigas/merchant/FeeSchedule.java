package com.orvigas.merchant;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Fee schedule applied to a merchant's transactions. Contains a default rate
 * and optional per-payment-method overrides (e.g. "amex" → higher rate).
 *
 * <p>Fee changes are forward-only: a new schedule takes effect at a specified
 * future date, and historical settlements keep the schedule that was active
 * at the time.
 *
 * @param defaultRate default fee applied when no method-specific override exists
 * @param overrides   per-method fee overrides keyed by payment method identifier
 * @author orvigas@gmail.com
 */
public record FeeSchedule(FeeEntry defaultRate, Map<String, FeeEntry> overrides) {

    public FeeSchedule {
        Objects.requireNonNull(defaultRate, "defaultRate must not be null");
        Objects.requireNonNull(overrides, "overrides must not be null");
    }

    /**
     * Returns an unmodifiable view of the overrides map.
     *
     * @return unmodifiable overrides
     */
    @Override
    public Map<String, FeeEntry> overrides() {
        return Collections.unmodifiableMap(overrides);
    }
}
