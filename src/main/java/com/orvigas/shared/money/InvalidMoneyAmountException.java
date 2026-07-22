package com.orvigas.shared.money;

/**
 * Thrown when a {@link Money} amount would be negative or would overflow the
 * long minor-units representation. Minor units are the only representation
 * the domain trusts, so these checks happen at construction and on every
 * arithmetic operation rather than being deferred to a formatting layer.
 *
 * @author orvigas@gmail.com
 */
public final class InvalidMoneyAmountException extends MoneyException {

    /**
     * Creates the exception describing why the amount is invalid.
     *
     * @param message human-readable description of the failure
     */
    public InvalidMoneyAmountException(String message) {
        super(message);
    }
}
