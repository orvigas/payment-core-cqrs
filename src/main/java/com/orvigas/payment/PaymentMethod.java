package com.orvigas.payment;

import java.util.Objects;

/**
 * Tokenized payment instrument reference. Contains only the provider token,
 * never raw PAN or credentials.
 *
 * @param token provider-assigned token or reference
 * @author orvigas@gmail.com
 */
public record PaymentMethod(String token) {

    /**
     * Validates the wrapped value.
     *
     * @throws NullPointerException if {@code token} is null
     */
    public PaymentMethod {
        Objects.requireNonNull(token, "token must not be null");
    }
}
