package com.orvigas.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers wiring for every test that needs a real Postgres or
 * MongoDB instance. R2DBC has no in-memory substitute, and the Axon MongoDB
 * event store extension has none either, so every integration test in this
 * codebase goes through real containers rather than an embedded database.
 *
 * <p>Containers are started once per JVM via a static initializer - the
 * Testcontainers "singleton container" pattern - and shared across every
 * subclass, wired into the Spring context automatically via
 * {@code @ServiceConnection}. This class deliberately does not use
 * {@code @Testcontainers}/{@code @Container}: those annotations tie start/stop
 * to each concrete test class's JUnit 5 lifecycle, but a {@code static} field
 * declared here is inherited, not duplicated, so every subclass shares the
 * exact same container instance. When two or more subclasses run in the same
 * JVM, {@code @Container}'s per-class {@code afterAll} stops that shared
 * instance out from under whichever class runs next, while Spring's test
 * context cache keeps reusing the now-stale connection details from before
 * the stop - the container gets restarted with a new mapped port that the
 * cached context never learns about, and every subsequent query fails with
 * "connection refused" on the old port. A static initializer starts each
 * container exactly once at class-load time, before any Spring context can
 * reference it, and never stops it explicitly; Testcontainers' Ryuk reaper
 * cleans both up when the JVM exits, same as it always did.
 *
 * <p>The Postgres tag matches the version pinned in {@code docker-compose.yml}
 * (15-alpine). The Mongo tag does not: {@code docker-compose.yml} and
 * {@code TECH_STACK.md} both pin {@code mongo:7.0-alpine}, but the official
 * image has never published an Alpine variant (Docker Hub only ships
 * jammy/Windows tags for it) - that tag 404s on pull. {@code 7.0-jammy} is
 * the closest real equivalent to what those docs intended; see the T-001
 * handoff log for the doc-fix that still needs to happen in
 * {@code docker-compose.yml}.
 *
 * <p>If Docker itself is unreachable, the static initializer fails at
 * class-load time with Testcontainers' own diagnostic (which strategy it
 * tried and why each failed) rather than a silent hang; Spring Boot's
 * {@code DockerEnvironmentNotFoundFailureAnalyzer} turns that into a
 * readable context-startup failure. See {@code docker} package for a test
 * that pins down this exact behavior.
 *
 * <p>{@code spring.config.location} points at a self-contained test-harness
 * YAML instead of layering on top of {@code src/main/resources/application.yml}:
 * that file's {@code spring.jackson.serialization.write-dates-as-timestamps}
 * key does not exist under Jackson 3's {@code SerializationFeature} enum
 * (part of the Boot 4.1 Jackson 3 migration), so binding it fails context
 * startup outright. Fixing that key is main-application config and out of
 * this task's scope; see the T-001 handoff log.
 *
 * @author orvigas@gmail.com
 */
@SpringBootTest(properties = "spring.config.location=classpath:/test-harness-application.yml")
public abstract class AbstractIntegrationTest {

    /** Backs the R2DBC read layer. */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"));

    /**
     * Backs the Axon event store and any reactive document access on the
     * write side. Replica-set mode is required, not optional: Axon's Mongo
     * token store uses multi-document transactions, and a standalone MongoDB
     * rejects those with "Transaction numbers are only allowed on a replica
     * set member or mongos" - a single-node replica set is the standard way
     * to satisfy that requirement in a test container.
     */
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer(DockerImageName.parse("mongo:7.0-jammy"))
            .withReplicaSet();

    static {
        POSTGRES.start();
        MONGODB.start();
    }
}
