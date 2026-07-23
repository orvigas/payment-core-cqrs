package com.orvigas.payment.api;

/**
 * Response representation of a monetary amount.
 *
 * @param minorUnits the amount in the currency's smallest unit
 * @param currency   ISO 4217 alphabetic currency code
 * @author orvigas@gmail.com
 */
public record MoneyResponse(
        long minorUnits,
        String currency) {
}
