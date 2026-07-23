package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a payment is authorized (approved or declined).
 *
 * @param paymentId the aggregate identifier
 * @param authorizedAmount amount the provider approved, or the requested amount on decline
 * @param authorizationCode provider's authorization reference, null on decline
 * @param failureReason provider decline reason, null on approval
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record PaymentAuthorized(
        PaymentId paymentId,
        Money authorizedAmount,
        String authorizationCode,
        FailureReason failureReason,
        Instant occurredAt) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if required fields are null
     * @throws IllegalArgumentException if both success and failure details are present, or neither
     */
    public PaymentAuthorized {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(authorizedAmount, "authorizedAmount must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        boolean hasSuccess = authorizationCode != null;
        boolean hasFailure = failureReason != null;
        if (hasSuccess == hasFailure) {
            throw new IllegalArgumentException("either authorizationCode or failureReason must be present, not both or neither");
        }
    }
}
