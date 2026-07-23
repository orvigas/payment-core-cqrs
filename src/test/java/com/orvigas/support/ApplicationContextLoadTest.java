package com.orvigas.support;

import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

/**
 * Proves the application context actually wires up against real
 * infrastructure, not just that bean creation didn't throw. A bare
 * {@code contextLoads()} would pass even if the R2DBC or Mongo connection
 * details were wrong, as long as no bean eagerly opens a connection; this
 * test forces both connections open and round-trips a real query so a
 * misconfigured container, wrong port, or bad credential fails here first.
 *
 * <p>The constructor takes only {@link ApplicationContext} -
 * {@code @SpringBootTest} enables constructor autowiring for test classes,
 * so no field or setter injection is needed here. The other beans are
 * looked up through it inside each test rather than added as further
 * constructor parameters: {@code DatabaseClient} is registered under the
 * bean name {@code r2dbcDatabaseClient}, and resolving it as one of several
 * simultaneous constructor parameters was unreliable in practice, while
 * {@code context.getBean(Type.class)} resolves purely by type and always
 * works.
 *
 * @author orvigas@gmail.com
 */
@RequiredArgsConstructor
class ApplicationContextLoadTest extends AbstractIntegrationTest {

    private final ApplicationContext context;

    @Test
    void contextLoads() {
        Assertions.assertThat(context).isNotNull();
    }

    @Test
    void r2dbcReadsFromRealPostgres() {
        DatabaseClient databaseClient = context.getBean(DatabaseClient.class);

        StepVerifier.create(databaseClient.sql("SELECT 1")
                        .map(row -> row.get(0, Integer.class))
                        .first())
                .expectNext(1)
                .verifyComplete();
    }

    @Test
    void reactiveMongoReachesRealMongodb() {
        ReactiveMongoTemplate mongoTemplate = context.getBean(ReactiveMongoTemplate.class);

        // Axon's event and token store beans create collections during context
        // startup, so an empty database isn't the right expectation. Listing
        // collections without error is enough to prove the driver can open a
        // socket and authenticate against the container, without depending on
        // any schema this task doesn't add.
        StepVerifier.create(mongoTemplate.getCollectionNames().collectList())
                .assertNext(collections -> Assertions.assertThat(collections).isNotEmpty())
                .verifyComplete();
    }
}
