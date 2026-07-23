package com.orvigas.payment;

import com.orvigas.shared.id.PaymentId;

/**
 * Outcome of handling an {@link InitiatePaymentCommand}: which payment the
 * idempotency key resolves to, and whether this call is the one that created
 * it. The REST layer needs both - the id to build the response body, and the
 * flag to pick 201 versus 200 - and neither is safe to reconstruct from a
 * locally generated id, since a deduplicated request never sees its own id
 * take effect.
 *
 * @param paymentId the payment identifier associated with the idempotency key
 * @param created   {@code true} if this call created the payment, {@code false} if a prior call already had
 * @author orvigas@gmail.com
 */
public record PaymentInitiationResult(PaymentId paymentId, boolean created) {
}
