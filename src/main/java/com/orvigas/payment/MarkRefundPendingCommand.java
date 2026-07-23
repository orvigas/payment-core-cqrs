package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Internal command: marks a refund as pending while the provider processes it.
 *
 * @param paymentId the aggregate identifier
 * @param refundId the refund entity being marked pending
 * @author orvigas@gmail.com
 */
public record MarkRefundPendingCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        RefundId refundId) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public MarkRefundPendingCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
    }
}
