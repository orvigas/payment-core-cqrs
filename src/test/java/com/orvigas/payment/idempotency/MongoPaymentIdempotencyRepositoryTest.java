package com.orvigas.payment.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.orvigas.shared.id.PaymentId;
import com.orvigas.support.AbstractIntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoDatabaseFactory;

/**
 * Verifies that {@link MongoPaymentIdempotencyRepository#tryStore} actually
 * closes the concurrent-request race it exists to prevent - see the class
 * Javadoc for why a plain {@code save()} would not.
 *
 * @author orvigas@gmail.com
 */
class MongoPaymentIdempotencyRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private MongoDatabaseFactory mongoDatabaseFactory;

    @Test
    void firstCallerClaimsTheKeyAndSecondFails() {
        MongoPaymentIdempotencyRepository repository = new MongoPaymentIdempotencyRepository(mongoDatabaseFactory);
        String key = UUID.randomUUID().toString();
        PaymentId first = PaymentId.newId();
        PaymentId second = PaymentId.newId();

        boolean firstWon = repository.tryStore(key, first);
        boolean secondWon = repository.tryStore(key, second);

        assertThat(firstWon).isTrue();
        assertThat(secondWon).isFalse();
        assertThat(repository.findPaymentIdByIdempotencyKey(key)).contains(first);
    }

    @Test
    void unknownKeyResolvesToEmpty() {
        MongoPaymentIdempotencyRepository repository = new MongoPaymentIdempotencyRepository(mongoDatabaseFactory);

        assertThat(repository.findPaymentIdByIdempotencyKey(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void exactlyOneOfManyConcurrentCallersClaimsTheSameKey() throws InterruptedException {
        MongoPaymentIdempotencyRepository repository = new MongoPaymentIdempotencyRepository(mongoDatabaseFactory);
        String key = UUID.randomUUID().toString();
        int callers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();

        try {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(start);
                    if (repository.tryStore(key, PaymentId.newId())) {
                        winners.incrementAndGet();
                    }
                });
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(winners.get()).isEqualTo(1);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
