package com.orvigas.payment.idempotency;

import com.orvigas.shared.id.PaymentId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.SessionSynchronization;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * MongoDB-backed idempotency store for payment initiation. The collection
 * key ({@code idempotencyKey}, mapped to {@code _id}) is unique by
 * construction - MongoDB always maintains a unique index on {@code _id}.
 *
 * <p>That index only closes a race if writes go through {@link
 * MongoTemplate#insert}. {@link MongoTemplate#save} does not: for a manually
 * assigned, non-null id, Spring Data treats the entity as "not new" and
 * issues an upsert against {@code _id} instead of a plain insert, so two
 * concurrent callers racing on the same key both succeed - the second
 * silently overwrites the first rather than failing. {@link #tryStore}
 * therefore uses {@code insert}, which lets MongoDB's own unique-index
 * enforcement reject the loser with a {@link DuplicateKeyException}.
 *
 * <p>This repository is called from {@code PaymentCommandHandler}, itself
 * running inside the same Axon {@code UnitOfWork} that also drives the
 * event store. Axon's replica-set event store keeps a MongoDB session bound
 * to that unit of work, and Spring Data's default {@link
 * SessionSynchronization#ON_ACTUAL_TRANSACTION} means the shared, Spring
 * Boot-managed {@code MongoTemplate} bean silently joins it. A duplicate-key
 * insert then aborts that shared session, and every subsequent Mongo
 * operation on the same thread - including the fallback lookup below -
 * fails with {@code NoSuchTransaction}, even though this collection has
 * nothing to do with event sourcing. This repository therefore builds its
 * own {@code MongoTemplate} over the same {@link MongoDatabaseFactory} with
 * {@link SessionSynchronization#NEVER}, so its reads and writes run as
 * ordinary standalone operations regardless of what else is happening on
 * the calling thread.
 *
 * @author orvigas@gmail.com
 */
@Repository
public class MongoPaymentIdempotencyRepository implements PaymentIdempotencyRepository {

    private final MongoTemplate mongoTemplate;

    public MongoPaymentIdempotencyRepository(MongoDatabaseFactory mongoDatabaseFactory) {
        this.mongoTemplate = new MongoTemplate(mongoDatabaseFactory);
        this.mongoTemplate.setSessionSynchronization(SessionSynchronization.NEVER);
    }

    @Override
    public Optional<PaymentId> findPaymentIdByIdempotencyKey(String idempotencyKey) {
        Query query = Query.query(Criteria.where("idempotencyKey").is(idempotencyKey));
        PaymentIdempotencyDocument document = mongoTemplate.findOne(query, PaymentIdempotencyDocument.class);
        return Optional.ofNullable(document).map(PaymentIdempotencyDocument::getPaymentId);
    }

    @Override
    public boolean tryStore(String idempotencyKey, PaymentId paymentId) {
        try {
            mongoTemplate.insert(new PaymentIdempotencyDocument(idempotencyKey, paymentId, Instant.now()));
            return true;
        } catch (DuplicateKeyException alreadyClaimed) {
            return false;
        }
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
