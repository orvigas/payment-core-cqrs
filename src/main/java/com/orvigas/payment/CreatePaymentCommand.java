package com.orvigas.payment;

import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Internal command that creates the Payment aggregate. The external
 * {@link InitiatePaymentCommand} is translated into this command after an
 * idempotency check, so the aggregate itself stays free of idempotency storage
 * concerns.
 *
 * @param paymentId the aggregate identifier
 * @param merchantId the merchant receiving funds
 * @param customerId the paying customer
 * @param amount requested amount
 * @param paymentMethod tokenized instrument reference
 * @param idempotencyKey client-supplied key for idempotent retries
 * @param authorizationExpiresAt deadline for capturing the authorization
 * @author orvigas@gmail.com
 */
public record CreatePaymentCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        MerchantId merchantId,
        CustomerId customerId,
        Money amount,
        PaymentMethod paymentMethod,
        String idempotencyKey,
        Instant authorizationExpiresAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     * @throws IllegalArgumentException if amount is not positive
     */
    public CreatePaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(paymentMethod, "paymentMethod must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(authorizationExpiresAt, "authorizationExpiresAt must not be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
