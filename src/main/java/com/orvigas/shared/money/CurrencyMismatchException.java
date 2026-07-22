package com.orvigas.shared.money;

import java.util.Currency;

/**
 * Thrown when a {@link Money} operation is attempted between amounts in
 * different currencies. Every payment fixes its currency at initiation, so
 * mixing currencies inside an arithmetic operation is always a bug, never a
 * recoverable business case.
 *
 * @author orvigas@gmail.com
 */
public final class CurrencyMismatchException extends MoneyException {

    /**
     * Creates the exception describing which currencies could not be
     * combined.
     *
     * @param expected currency of the left-hand operand
     * @param actual currency of the right-hand operand
     */
    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("currency mismatch: expected %s but got %s".formatted(
                expected.getCurrencyCode(), actual.getCurrencyCode()));
    }
}
