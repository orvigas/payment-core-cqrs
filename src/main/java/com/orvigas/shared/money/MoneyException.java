package com.orvigas.shared.money;

/**
 * Root of the sealed exception hierarchy for {@link Money} rule violations.
 * Kept sealed so every failure mode a caller might need to handle is
 * enumerable at compile time instead of discovered at runtime.
 *
 * @author orvigas@gmail.com
 */
public sealed abstract class MoneyException extends RuntimeException
        permits CurrencyMismatchException, InvalidMoneyAmountException {

    /**
     * Creates the exception with a message describing the violated rule.
     *
     * @param message human-readable description of the failure
     */
    protected MoneyException(String message) {
        super(message);
    }
}
