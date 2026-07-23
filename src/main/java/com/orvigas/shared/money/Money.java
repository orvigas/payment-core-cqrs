package com.orvigas.shared.money;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in a single ISO 4217 currency, represented as integer
 * minor units (cents, pence, and so on). Floating-point types are never used
 * here because binary floating point cannot represent decimal fractions
 * exactly, and rounding drift in a payments ledger is not acceptable.
 *
 * <p>Amounts are always non-negative. A negative movement of money is
 * expressed by which side of an operation it appears on (a debit, a refund),
 * not by a negative {@code Money} value — this mirrors how the domain model
 * in {@code knowledge/domain/payment.md} treats amounts.
 *
 * @param minorUnits the amount in the currency's smallest unit, never negative
 * @param currency the ISO 4217 currency the amount is denominated in
 * @author orvigas@gmail.com
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    /**
     * Validates invariants shared by every construction path.
     *
     * @throws InvalidMoneyAmountException if {@code minorUnits} is negative
     */
    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
        if (minorUnits < 0) {
            throw new InvalidMoneyAmountException(
                    "amount cannot be negative: " + minorUnits + " " + currency.getCurrencyCode());
        }
    }

    /**
     * Creates a {@code Money} value from an ISO 4217 currency code.
     *
     * @param minorUnits the amount in the currency's smallest unit
     * @param currencyCode ISO 4217 alphabetic currency code, e.g. {@code "USD"}
     * @return the constructed amount
     * @throws IllegalArgumentException if {@code currencyCode} is not a valid ISO 4217 code
     * @throws InvalidMoneyAmountException if {@code minorUnits} is negative
     */
    public static Money of(long minorUnits, String currencyCode) {
        return new Money(minorUnits, Currency.getInstance(currencyCode));
    }

    /**
     * Returns the zero amount for the given currency. Used as the starting
     * point for running totals such as {@code capturedAmount}.
     *
     * @param currency the currency of the zero amount
     * @return a zero-value {@code Money} in the given currency
     */
    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    /**
     * Adds another amount in the same currency.
     *
     * @param other the amount to add
     * @return the sum
     * @throws CurrencyMismatchException if {@code other} is in a different currency
     * @throws InvalidMoneyAmountException if the sum overflows a {@code long}
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        try {
            return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
        } catch (ArithmeticException overflow) {
            throw new InvalidMoneyAmountException(
                    "adding " + other.minorUnits + " to " + minorUnits + " " + currency.getCurrencyCode()
                            + " overflows the minor-units representation");
        }
    }

    /**
     * Subtracts another amount in the same currency.
     *
     * @param other the amount to subtract
     * @return the difference
     * @throws CurrencyMismatchException if {@code other} is in a different currency
     * @throws InvalidMoneyAmountException if the result would be negative
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        long result = minorUnits - other.minorUnits;
        if (result < 0) {
            throw new InvalidMoneyAmountException(
                    "subtracting " + other.minorUnits + " from " + minorUnits + " " + currency.getCurrencyCode()
                            + " would produce a negative amount");
        }
        return new Money(result, currency);
    }

    /**
     * Reports whether this amount is strictly greater than zero.
     *
     * @return {@code true} if {@code minorUnits} is greater than zero
     */
    @JsonIgnore
    public boolean isPositive() {
        return minorUnits > 0;
    }

    /**
     * Reports whether this amount is exactly zero.
     *
     * @return {@code true} if {@code minorUnits} is zero
     */
    @JsonIgnore
    public boolean isZero() {
        return minorUnits == 0;
    }

    /**
     * Compares amounts by magnitude. Both values must share a currency —
     * ordering amounts across currencies without a conversion rate is not a
     * meaningful operation, so it is rejected rather than silently compared
     * by minor units alone.
     *
     * @param other the amount to compare against
     * @return a negative number, zero, or a positive number as this amount is
     *     less than, equal to, or greater than {@code other}
     * @throws CurrencyMismatchException if {@code other} is in a different currency
     */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
