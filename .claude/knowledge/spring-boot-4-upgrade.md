# Spring Boot 3.5 to 4.1 Upgrade Notes

## What changed

In July 2026 the governance docs were updated to target Spring Boot 4.1.0 (GA June 10, 2026) instead of 3.5.16. Boot 4 modularized its autoconfiguration and moved to Spring Framework 7 / Spring Security 7, which breaks several dependencies that worked untouched on Boot 3. These are the coordinated bumps required, verified against release announcements and upstream issue trackers.

## Required companion changes

- **Spring Kafka 4.1.0** (GA June 9, 2026, released in lockstep with Boot 4.1.0). Boot 4 requires the new `spring-boot-starter-kafka` starter; declaring only `spring-kafka` compiles but the Kafka autoconfiguration never loads (spring-kafka issue 4278). `spring-kafka-test` moves to the same version.
- **SpringDoc 3.0.3.** The 2.8.x line targets Boot 3 and fails on Boot 4 because `WebFluxProperties` moved from `org.springframework.boot.autoconfigure.web.reactive` to `org.springframework.boot.webflux.autoconfigure` (springdoc issue 3196). Only the 3.x line supports Boot 4. Still `springdoc-openapi-starter-webflux-ui`, never the WebMVC artifact.
- **Resilience4j: swap `resilience4j-spring-boot3` for `resilience4j-spring-boot4`**, available since 2.4.0. The new artifact was initially omitted from the resilience4j BOM, so declare its version explicitly rather than relying on the BOM (resilience4j issues 2351, 2427).
- **Micrometer artifacts follow the Boot BOM.** The old explicit pins (`micrometer-registry-prometheus` 1.15.12, `micrometer-tracing-bridge-brave` 1.5.12) matched Boot 3.5's managed versions; re-pinning them under Boot 4.1 would downgrade what the BOM manages. TECH_STACK.md now marks them "via Boot BOM".

## Found by the first real build (2026-07-22)

The pom had never been built until the quality gate was wired up; the first `mvn verify` exposed three more Boot-4-era issues:

- **Axon 4.10.5 does not exist.** The 4.10.x line on Maven Central ends at 4.10.4 (latest overall: 4.13.3). The 4.10.4 `axon-bom` manages the MongoDB extension at 4.10.0 — the extension versions independently of the framework. A pinned version is only trustworthy after a build has resolved it.
- **Testcontainers 2.x renamed its module artifacts.** Boot 4.1 manages `testcontainers-bom` 2.0.5; the 1.x artifact ids `junit-jupiter`, `postgresql`, and `mongodb` no longer resolve. The new names are `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-mongodb`.
- **Mockito setup was doubly broken.** `mockito-inline` is discontinued (inline mocking is the Mockito 5 default; the artifact ends at 5.2.0), and attaching mockito-core 5.2.0 as a `-javaagent` crashes the JVM at startup (`Agent_OnLoad: instrument`) because agent support only arrived around 5.14. Worse, the hardcoded surefire `argLine` silently replaced JaCoCo's `prepare-agent` argument, which would have produced empty coverage data. Fix: drop `mockito-inline`, resolve the BOM-managed jar via the dependency plugin's `properties` goal, and compose `argLine` as `@{argLine} -javaagent:${org.mockito:mockito-core:jar}`.

## Lombok silently no-ops without an explicit annotation processor path (2026-07-22)

Found while adding the T-001 test harness: `@RequiredArgsConstructor` on a test class compiled with no error, but the constructor was never generated - the field stayed uninitialized and javac reported "variable not initialized in the default constructor" as if the annotation weren't there at all.

Root cause: this toolchain runs Maven under JDK 23, and its javac no longer treats plain `-classpath` entries as a source of annotation processors. Lombok was only ever declared as a regular `provided` dependency, so it was reachable on the classpath but never picked up as a processor - and critically, javac does not warn when this happens; a class using Lombok annotations just compiles as if they were absent. Reproduced directly with `javac` outside Maven: `-cp lombok.jar` alone silently drops the annotation, while `-processorpath lombok.jar` runs it correctly.

Fix: give `maven-compiler-plugin` an explicit `annotationProcessorPaths` entry for `org.projectlombok:lombok:${lombok.version}`. This was previously missing because no main-source class used Lombok yet, so the gap was latent rather than broken.

Why it matters: `CODING_STANDARD.md` mandates `@RequiredArgsConstructor`/`@Slf4j` as the standard pattern. Without this fix, every future task that adds a Lombok-annotated class would hit the same silent failure - not a build error, but a runtime `NullPointerException` or missing logger the first time the class is actually exercised, since the compiler never complained.

Prevention: covered structurally now (the fix is in `pom.xml`), and the harness's own `ApplicationContextLoadTest` uses `@RequiredArgsConstructor` for its constructor injection, so a regression here would resurface immediately as a compile failure rather than staying latent again.

## More Boot-4 artifact renames found during T-004 (2026-07-22)

- **`spring-boot-starter-aop` is now `spring-boot-starter-aspectj`.** The old artifact id is gone in Boot 4.1. Without the new starter, AspectJ-based AOP is not configured, and beans like `RateLimiterAspect` are never created even though AspectJ is on the classpath.
- **`spring-boot-webtestclient` is a separate test artifact.** The `@AutoConfigureWebTestClient` annotation moved from `org.springframework.boot.test.autoconfigure.web.reactive` to `org.springframework.boot.webtestclient.autoconfigure`. If the import fails or the `WebTestClient` bean is missing, add the artifact and update the import.

## Why 4.1.0 and not 4.0.x

4.1.0 is the latest stable line (active support to July 2027); 4.0.x support ends December 2026. New projects should target 4.1.

## How to avoid regressions

When bumping the Boot parent in the future, check every dependency whose artifact name encodes the Boot generation (`resilience4j-spring-boot4`, springdoc major line) and any starter the modularized autoconfiguration split out — a missing starter fails silently at runtime, not at compile time.

## Found during T-008 Payment REST API (2026-07-22)

### MongoDB UUID representation must be explicit

Axon's MongoDB event store and Spring Data MongoDB's `MongoTemplate` both fail with `CodecConfigurationException: The uuidRepresentation has not been specified, so the UUID cannot be encoded.` when trying to store entities containing `java.util.UUID` fields (such as payment aggregate identifiers) unless `UuidRepresentation.STANDARD` is configured.

Fix: register a `MongoClientSettingsBuilderCustomizer` bean that sets `uuidRepresentation(UuidRepresentation.STANDARD)`.

In Spring Boot 4.1, the customizer interface moved to `org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer` (previously `org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer` in Boot 3.x). The old package no longer exists — import the new one.

The `spring.data.mongodb.uuid-representation: standard` property (used in Boot 3.x) does NOT take effect in Boot 4.1 because the property-based configuration path changed; only the programmatic customizer works reliably.

### Jackson detects boolean `is*()` methods as extra properties on records

When a record (or class) has methods named `isZero()` or `isPositive()`, Jackson's default property detection treats them as boolean properties `zero` and `positive`. These get serialized alongside the actual record components. When Axon's event store stores serialized events containing such types (e.g., `Money`) and replays them later, deserialization fails with `UnrecognizedPropertyException` because the auto-detected properties aren't constructor parameters.

Fix: annotate `isZero()` and `isPositive()` with `@JsonIgnore`, and add `@JsonIgnoreProperties(ignoreUnknown = true)` at the class level to remain backward-compatible with events serialized before the fix (which contain the extra fields).

### `@Transactional` is incompatible with multiple TransactionManager beans

The `@Transactional` annotation on a service method fails when two `TransactionManager` beans exist (e.g., `mongoTransactionManager` from Axon and `connectionFactoryTransactionManager` from R2DBC). Spring cannot determine which one to use. In a CQRS/Axon project where event sourcing manages consistency, `@Transactional` on the service layer is neither needed nor safe — remove it.

### Void-returning command handlers require `.then()` not `.map()`

When a command handler returns `void` (as most aggregate handlers do), `commandGateway.send(command)` returns a `CompletableFuture<Object>` that completes with `null`. Using `Mono.fromFuture(future).map(result -> response)` silently produces an empty `Mono<Void>` because Reactor suppresses null values in `onNext`. The fix is `Mono.fromFuture(future).then(Mono.just(response))`.

## Keywords

spring boot 4, upgrade, migration, spring kafka starter, springdoc 3, resilience4j-spring-boot4, WebFluxProperties, boot bom, lombok annotationProcessorPaths, javac JDK 23 annotation processor discovery, RequiredArgsConstructor silent no-op, mongodb uuid representation, MongoClientSettingsBuilderCustomizer, jackson isZero detection, void command handler Mono.fromFuture then

Related: [[r2dbc-migration-gotchas]], [[distributed-tracing-observability-gotchas]]
