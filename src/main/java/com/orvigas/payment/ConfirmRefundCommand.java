package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Internal command: confirms that the payment provider succeeded on a refund.
 *
 * @param paymentId the aggregate identifier
 * @param refundId the refund entity being confirmed
 * @param providerReference provider's identifier for this refund
 * @author orvigas@gmail.com
 */
public record ConfirmRefundCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        RefundId refundId,
        String providerReference) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public ConfirmRefundCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(providerReference, "providerReference must not be null");
    }
}
