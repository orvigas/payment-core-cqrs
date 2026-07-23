package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Requests a refund of previously captured funds.
 *
 * @param paymentId the aggregate identifier
 * @param amount amount to refund
 * @param captureId the capture being refunded, when the provider requires it; otherwise null
 * @param reason structured reason plus optional notes
 * @param idempotencyKey client-supplied key for idempotent retries
 * @param initiatedBy who triggered the refund
 * @author orvigas@gmail.com
 */
public record RefundPaymentCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        Money amount,
        CaptureId captureId,
        RefundReason reason,
        String idempotencyKey,
        RefundInitiator initiatedBy) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any required field is null
     * @throws IllegalArgumentException if amount is not positive
     */
    public RefundPaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(initiatedBy, "initiatedBy must not be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
