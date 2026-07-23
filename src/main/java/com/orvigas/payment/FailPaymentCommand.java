package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Marks a payment as failed with the given reason.
 *
 * @param paymentId the aggregate identifier
 * @param reason failure details
 * @author orvigas@gmail.com
 */
public record FailPaymentCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        FailureReason reason) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public FailPaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
