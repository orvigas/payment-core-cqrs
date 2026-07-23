package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Marks a payment as expired when the authorization expires. Triggered by a
 * scheduled deadline, not by a polling mechanism.
 *
 * @param paymentId the aggregate identifier
 * @author orvigas@gmail.com
 */
public record ExpirePaymentCommand(@TargetAggregateIdentifier PaymentId paymentId) {

    /**
     * Validates the field.
     *
     * @throws NullPointerException if {@code paymentId} is null
     */
    public ExpirePaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
    }
}
