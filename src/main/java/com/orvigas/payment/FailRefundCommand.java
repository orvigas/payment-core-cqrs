package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Internal command: records that the payment provider declined a refund.
 *
 * @param paymentId the aggregate identifier
 * @param refundId the refund entity that failed
 * @param reason provider decline reason
 * @author orvigas@gmail.com
 */
public record FailRefundCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        RefundId refundId,
        FailureReason reason) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public FailRefundCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
