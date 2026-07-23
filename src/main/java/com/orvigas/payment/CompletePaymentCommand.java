package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Marks a payment as completed after all intended captures have succeeded.
 *
 * @param paymentId the aggregate identifier
 * @author orvigas@gmail.com
 */
public record CompletePaymentCommand(@TargetAggregateIdentifier PaymentId paymentId) {

    /**
     * Validates the field.
     *
     * @throws NullPointerException if {@code paymentId} is null
     */
    public CompletePaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
    }
}
