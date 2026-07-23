package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.PaymentId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Internal command: records that the payment provider declined a capture.
 *
 * @param paymentId the aggregate identifier
 * @param captureId the capture entity that failed
 * @param reason provider decline reason
 * @author orvigas@gmail.com
 */
public record FailCaptureCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        CaptureId captureId,
        FailureReason reason) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public FailCaptureCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(captureId, "captureId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
