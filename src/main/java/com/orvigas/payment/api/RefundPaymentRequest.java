package com.orvigas.payment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for refunding captured funds.
 *
 * @param amount         amount to refund
 * @param reason         structured reason for the refund
 * @param idempotencyKey client-supplied key for idempotent retries
 * @author orvigas@gmail.com
 */
public record RefundPaymentRequest(
        @NotNull @Valid MoneyRequest amount,
        @NotNull @Valid RefundReasonRequest reason,
        @NotBlank String idempotencyKey) {
}
