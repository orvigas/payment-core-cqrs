# @Container on a static field in a shared base class restarts per subclass

- Date: 2026-07-22
- Affected versions/components: Testcontainers 2.0.5, JUnit 5, `AbstractIntegrationTest` (from T-001), Spring Boot Test's `@ServiceConnection`

## Problem

`mvn verify` started failing intermittently once the test suite grew past a handful of classes extending `AbstractIntegrationTest`/`AbstractSecurityIntegrationTest`. The failure was always the same shape: a test hitting `/actuator/health` (or any R2DBC-backed path) would time out or get `Connection refused` on a port that had worked fine moments earlier in the same run. Running the failing class alone always passed; it only failed when run after certain other classes.

## Root cause

`AbstractIntegrationTest` declared its Testcontainers like this:

```java
@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(...);
}
```

`static final` fields declared in a superclass are inherited, not duplicated - there is exactly one `POSTGRES` object shared by every subclass. But `@Testcontainers`/`@Container` ties container start/stop to JUnit 5's per-class lifecycle (`beforeAll`/`afterAll`), and that lifecycle is evaluated fresh for *every concrete test class*, not once for the class that declared the field. The result: class A's `afterAll` calls `.stop()` on the (shared) container, then class B's `beforeAll` calls `.start()` on it again - which creates a **new** container with a **new** mapped port, not a restart of the same one.

Spring Boot's test context caching makes this worse rather than better: if class A and class B have equivalent `@SpringBootTest` configuration, Spring reuses the same cached `ApplicationContext` for both - including the R2DBC connection factory bean, which was wired to the *old* container's port when the context was first built. Class B ends up running against a stale connection pointed at a container that class A already tore down.

Confirmed by watching `docker ps` during a two-class repro: two distinct Postgres container IDs on two different ports appeared during a single `mvn test` run, even though the code only ever declares one `POSTGRES` field.

## Diagnosis

- The failure was consistently in whichever `@ServiceConnection`-backed test happened to run *after* another such class in the same JVM, never when run in isolation - a strong signal of shared, mutated state rather than a logic bug.
- `docker ps` polled mid-run showed two different container IDs/ports for the same image, which is the smoking gun: something is tearing down and recreating the "shared" container.

## Fix

Removed `@Testcontainers`/`@Container` and started both containers exactly once, in a static initializer, using Testcontainers' own documented "singleton container" pattern for base classes shared across test classes:

```java
public abstract class AbstractIntegrationTest {
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(...);

    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer(...).withReplicaSet();

    static {
        POSTGRES.start();
        MONGODB.start();
    }
}
```

`@ServiceConnection` doesn't require `@Container`'s lifecycle hooks - it only needs the container to already be running (host/port available) by the time Spring builds the context, and a static initializer guarantees that at class-load time, which happens exactly once per JVM regardless of how many subclasses run. Nothing calls `.stop()` explicitly; Testcontainers' Ryuk reaper cleans both containers up when the JVM exits, same as before.

## Why the solution works

Static initializer blocks run once, the first time the declaring class is loaded by the classloader - not once per subclass, and not tied to any test framework's lifecycle callbacks. That matches what was actually wanted ("one container for the whole suite") far more directly than `@Container`'s per-class semantics, which only happen to look like "start once" when there's a single concrete subclass in play.

## Trade-offs and limitations

No per-class isolation: if a test ever needs a genuinely fresh database (not just a clean schema), it can't use this base class as-is. Nothing in this codebase currently needs that. Containers also stay running for the whole `mvn verify` process rather than being torn down between classes, which is the intended trade for speed and is how Testcontainers' own docs describe this exact pattern.

## How to avoid a recurrence

Never combine `@Container` with a `static` field declared in a class meant to be *extended* by multiple test classes. Either keep `@Container` and declare the field directly in each concrete test class (real one-container-per-class isolation), or drop `@Container` entirely and use a static initializer for a genuinely shared singleton, per Testcontainers' own "Singleton Containers" pattern. Mixing the two - static field, per-class annotation - silently produces neither behavior correctly.

## Keywords

testcontainers, singleton container, @Container, @ServiceConnection, static field, JUnit 5 lifecycle, connection refused, stale port, Spring test context caching, flaky test
