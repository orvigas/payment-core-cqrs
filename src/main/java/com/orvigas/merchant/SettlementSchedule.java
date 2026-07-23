package com.orvigas.merchant;

import java.util.Objects;

/**
 * Payout cadence and delay for merchant settlements.
 *
 * @param frequency daily or weekly payout cycle
 * @param delayDays business-day delay after the period ends (e.g. T+2)
 * @author orvigas@gmail.com
 */
public record SettlementSchedule(SettlementFrequency frequency, int delayDays) {

    public SettlementSchedule {
        Objects.requireNonNull(frequency, "frequency must not be null");
        if (delayDays < 0) {
            throw new IllegalArgumentException("delayDays must not be negative");
        }
    }
}
