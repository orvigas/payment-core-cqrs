package com.orvigas.payment.idempotency;

import com.orvigas.shared.id.PaymentId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * MongoDB-backed idempotency store for payment initiation. The collection is
 * scoped to this bounded context and relies on a unique index on the key.
 *
 * @author orvigas@gmail.com
 */
@Repository
public class MongoPaymentIdempotencyRepository implements PaymentIdempotencyRepository {

    private final MongoTemplate mongoTemplate;

    public MongoPaymentIdempotencyRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<PaymentId> findPaymentIdByIdempotencyKey(String idempotencyKey) {
        Query query = Query.query(Criteria.where("idempotencyKey").is(idempotencyKey));
        PaymentIdempotencyDocument document = mongoTemplate.findOne(query, PaymentIdempotencyDocument.class);
        return Optional.ofNullable(document).map(PaymentIdempotencyDocument::getPaymentId);
    }

    @Override
    public void store(String idempotencyKey, PaymentId paymentId) {
        mongoTemplate.save(new PaymentIdempotencyDocument(idempotencyKey, paymentId, Instant.now()));
    }

    /**
     * Stored mapping from idempotency key to payment id.
     */
    @Document(collection = "payment_idempotency_keys")
    static class PaymentIdempotencyDocument {
        @Id
        private final String idempotencyKey;
        private final PaymentId paymentId;
        private final Instant storedAt;

        PaymentIdempotencyDocument(String idempotencyKey, PaymentId paymentId, Instant storedAt) {
            this.idempotencyKey = idempotencyKey;
            this.paymentId = paymentId;
            this.storedAt = storedAt;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        public PaymentId getPaymentId() {
            return paymentId;
        }

        public Instant getStoredAt() {
            return storedAt;
        }
    }
}
