package com.orvigas.merchant;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Fee rate for a single payment method. The {@code percentage} is applied to
 * the transaction amount; {@code fixedFeeMinorUnits} is charged in the
 * transaction's currency regardless of amount.
 *
 * @param percentage        percentage fee (e.g. 2.9 represents 2.9 %)
 * @param fixedFeeMinorUnits fixed per-transaction fee in the currency's minor unit
 * @param currencyCode      ISO 4217 currency code for the fixed fee
 * @author orvigas@gmail.com
 */
public record FeeEntry(BigDecimal percentage, long fixedFeeMinorUnits, String currencyCode) {

    public FeeEntry {
        Objects.requireNonNull(percentage, "percentage must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("percentage must not be negative");
        }
        if (fixedFeeMinorUnits < 0) {
            throw new IllegalArgumentException("fixedFeeMinorUnits must not be negative");
        }
    }
}
