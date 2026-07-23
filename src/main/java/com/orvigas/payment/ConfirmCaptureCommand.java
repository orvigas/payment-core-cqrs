package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.PaymentId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Internal command: confirms that the payment provider succeeded on a capture.
 *
 * @param paymentId the aggregate identifier
 * @param captureId the capture entity being confirmed
 * @param providerReference provider's identifier for this capture
 * @author orvigas@gmail.com
 */
public record ConfirmCaptureCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        CaptureId captureId,
        String providerReference) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if any field is null
     */
    public ConfirmCaptureCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(captureId, "captureId must not be null");
        Objects.requireNonNull(providerReference, "providerReference must not be null");
    }
}
