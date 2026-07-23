package com.orvigas.payment.idempotency;

import com.orvigas.shared.id.PaymentId;
import java.util.Optional;

/**
 * Records the mapping from client idempotency keys to payment aggregate
 * identifiers. Implementations are responsible for the chosen durability and
 * TTL semantics.
 *
 * @author orvigas@gmail.com
 */
public interface PaymentIdempotencyRepository {

    /**
     * Returns the payment identifier previously associated with the given
     * idempotency key, if any.
     *
     * @param idempotencyKey the client-supplied key
     * @return the existing payment id, or empty if the key is unknown
     */
    Optional<PaymentId> findPaymentIdByIdempotencyKey(String idempotencyKey);

    /**
     * Stores the mapping from idempotency key to payment identifier.
     *
     * @param idempotencyKey the client-supplied key
     * @param paymentId the payment created for this key
     */
    void store(String idempotencyKey, PaymentId paymentId);
}
