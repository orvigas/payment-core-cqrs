package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Captures (charges) part or all of an authorized amount.
 *
 * @param paymentId the aggregate identifier
 * @param amount amount to capture
 * @param isFinal whether this is the final capture for this authorization
 * @param captureId an explicit capture identifier, generated if null
 * @author orvigas@gmail.com
 */
public record CapturePaymentCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        Money amount,
        boolean isFinal,
        CaptureId captureId) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any required field is null
     * @throws IllegalArgumentException if amount is not positive
     */
    public CapturePaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    /**
     * Creates a capture command without an explicit capture identifier; the
     * aggregate will generate one.
     *
     * @param paymentId the aggregate identifier
     * @param amount amount to capture
     * @param isFinal whether this is the final capture
     */
    public CapturePaymentCommand(PaymentId paymentId, Money amount, boolean isFinal) {
        this(paymentId, amount, isFinal, null);
    }
}
