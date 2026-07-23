package com.orvigas.payment;

/**
 * Thrown when a capture or refund command targets a payment that does not
 * belong to the caller's merchant.
 *
 * <p>Mapped to the same 404 response as a genuinely unknown payment id (see
 * {@code GlobalErrorHandler}), so a caller probing payment ids cannot tell
 * "not yours" apart from "does not exist" - returning a distinct 403 here
 * would itself leak that the id is valid.
 *
 * @author orvigas@gmail.com
 */
public class PaymentAccessDeniedException extends RuntimeException {

    /**
     * Creates the exception with a server-side diagnostic message.
     *
     * @param message detail for the server log; never sent to the client
     */
    public PaymentAccessDeniedException(String message) {
        super(message);
    }
}
