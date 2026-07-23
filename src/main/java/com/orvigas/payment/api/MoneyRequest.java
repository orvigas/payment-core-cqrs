package com.orvigas.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request representation of a monetary amount in the currency's smallest unit.
 *
 * @param minorUnits the amount in the currency's smallest unit (e.g., cents for USD)
 * @param currency   ISO 4217 alphabetic currency code (e.g., "USD")
 * @author orvigas@gmail.com
 */
public record MoneyRequest(
        @Positive long minorUnits,
        @NotBlank String currency) {
}
