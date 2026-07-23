package com.orvigas.payment.idempotency;

import com.orvigas.shared.id.PaymentId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory idempotency store for unit and aggregate tests. Not suitable for
 * production because it is scoped to a single JVM and lost on restart.
 *
 * @author orvigas@gmail.com
 */
public class InMemoryPaymentIdempotencyRepository implements PaymentIdempotencyRepository {

    private final Map<String, PaymentId> store = new HashMap<>();

    @Override
    public Optional<PaymentId> findPaymentIdByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(store.get(idempotencyKey));
    }

    @Override
    public void store(String idempotencyKey, PaymentId paymentId) {
        store.put(idempotencyKey, paymentId);
    }
}
