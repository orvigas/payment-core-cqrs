package com.orvigas.merchant;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * Rolling reserve configuration for a merchant. A percentage of each settlement is
 * withheld for the configured hold duration to cover refunds and chargebacks after
 * a merchant churns or fails.
 *
 * @param percentage  percentage of each settlement to withhold (0–100)
 * @param holdDuration how long withheld funds are held before release
 * @author orvigas@gmail.com
 */
public record ReserveConfig(BigDecimal percentage, Duration holdDuration) {

    public ReserveConfig {
        Objects.requireNonNull(percentage, "percentage must not be null");
        Objects.requireNonNull(holdDuration, "holdDuration must not be null");
        if (percentage.compareTo(BigDecimal.ZERO) < 0
                || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }
    }
}
