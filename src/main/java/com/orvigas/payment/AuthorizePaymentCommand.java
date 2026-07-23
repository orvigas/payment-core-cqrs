package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Authorizes a payment after the provider responds. Contains the provider's
 * result: either approval (with authorization code and authorized amount) or
 * a decline (with failure reason).
 *
 * @param paymentId the aggregate identifier
 * @param authorizedAmount amount the provider approved
 * @param authorizationCode provider's authorization reference, null on decline
 * @param failureReason provider decline reason, null on approval
 * @author orvigas@gmail.com
 */
public record AuthorizePaymentCommand(
        @TargetAggregateIdentifier PaymentId paymentId,
        Money authorizedAmount,
        String authorizationCode,
        FailureReason failureReason) {

    /**
     * Validates the fields.
     *
     * @throws NullPointerException if required fields are null
     * @throws IllegalArgumentException if both success and failure details are present, or neither
     */
    public AuthorizePaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(authorizedAmount, "authorizedAmount must not be null");
        if (!authorizedAmount.isPositive()) {
            throw new IllegalArgumentException("authorizedAmount must be positive");
        }
        boolean hasSuccess = authorizationCode != null;
        boolean hasFailure = failureReason != null;
        if (hasSuccess == hasFailure) {
            throw new IllegalArgumentException("either authorizationCode or failureReason must be present, not both or neither");
        }
    }
}
