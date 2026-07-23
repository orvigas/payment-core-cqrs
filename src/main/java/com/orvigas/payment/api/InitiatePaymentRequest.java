package com.orvigas.payment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for initiating a new payment.
 *
 * @param merchantId        the merchant receiving funds
 * @param customerId        the paying customer
 * @param amount            requested amount
 * @param paymentMethodToken tokenized instrument reference
 * @param idempotencyKey    client-supplied key for idempotent retries
 * @author orvigas@gmail.com
 */
public record InitiatePaymentRequest(
        @NotNull String merchantId,
        @NotNull String customerId,
        @NotNull @Valid MoneyRequest amount,
        @NotBlank String paymentMethodToken,
        @NotBlank String idempotencyKey) {
}
