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
     * Atomically claims an idempotency key for the given payment id. This is
     * the only safe way to reserve a key: a plain find-then-store from the
     * caller leaves a window where two concurrent requests both see the key
     * as unused and both create a payment. Implementations must guarantee
     * that of any number of concurrent callers racing on the same key,
     * exactly one gets {@code true}.
     *
     * @param idempotencyKey the client-supplied key
     * @param paymentId the payment id this call wants to associate with the key
     * @return {@code true} if this call won the race and the key is now reserved for
     *         {@code paymentId}; {@code false} if another call already claimed the key first
     */
    boolean tryStore(String idempotencyKey, PaymentId paymentId);
}
