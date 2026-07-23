package com.orvigas.payment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for capturing authorized funds.
 *
 * @param amount  amount to capture
 * @param isFinal whether this is the final capture for this authorization
 * @author orvigas@gmail.com
 */
public record CapturePaymentRequest(
        @NotNull @Valid MoneyRequest amount,
        boolean isFinal) {
}
