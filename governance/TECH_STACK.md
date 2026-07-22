# Tech Stack

Reference for every framework, library, and infrastructure component used in Payment Core. Versions are pinned from `pom.xml` and `docker-compose.yml` as of `1.3.0`.

## Overview

Payment Core is a fully reactive Spring WebFlux service: controllers, services, and repositories all operate on `Mono`/`Flux`, backed by Spring Data R2DBC instead of JPA. There is no blocking I/O on the request path. Kafka consumers are the one intentional exception — `@KafkaListener` methods stay synchronous because they run on their own consumer threads, not the Netty event loop.

## Core Runtime

| Component | Version | Notes |
|---|---|---|
| Java | 21 (LTS) | Records, pattern matching; compiled with `--release 21` |
| Spring Boot | 4.1.0 | Parent BOM controls most dependency versions below; brings Spring Framework 7.1 and Spring Security 7 |
| Build tool | Maven | `maven-compiler-plugin` 3.13.0 |

## Web Layer

| Component | Version | Notes |
|---|---|---|
| Spring WebFlux | via Boot BOM | Netty server, non-blocking end to end |
| SpringDoc OpenAPI | 3.0.3 | `springdoc-openapi-starter-webflux-ui` — the WebMVC artifact is incompatible with this stack; the 3.x line is required for Spring Boot 4 (2.8.x targets Boot 3 and breaks against the relocated `WebFluxProperties`) |

Swagger UI: `/swagger-ui.html`. OpenAPI spec: `/v3/api-docs`.

## Persistence

### Write Layer (Commands via Axon)

| Component | Version | Notes |
|---|---|---|
| Axon Framework | 4.10.4 | CQRS orchestration; command handling, event sourcing, aggregate lifecycle. 4.10.5 was pinned originally but never existed on Maven Central; 4.10.4 closes the 4.10.x line |
| Axon MongoDB Extension | 4.10.0 (via axon-bom) | Event store, snapshot store, and token store backed by MongoDB; the extension versions independently and the 4.10.4 BOM pins it to 4.10.0 |
| Spring Data MongoDB | via Boot BOM | Reactive document persistence for write projections and command models |
| mongodb-driver-reactivestreams | via Boot BOM | Async MongoDB driver for reactive stack |
| MongoDB (runtime) | 7.0-alpine (Docker image) | Event store and command model storage |

### Read Layer (Queries via Postgres)

| Component | Version | Notes |
|---|---|---|
| Spring Data R2DBC | via Boot BOM | Reactive persistence for read projections; no Hibernate, no `EntityManager` |
| r2dbc-postgresql | via Boot BOM | Runtime-scoped R2DBC driver, loaded via SPI — no application code references it directly |
| PostgreSQL JDBC driver | 42.7.11 | Runtime-scoped, used exclusively by Flyway (which has no R2DBC support) |
| Flyway (`flyway-core`, `flyway-database-postgresql`) | 11.7.2 | Read-layer schema migrations in `src/main/resources/db/migration` |
| PostgreSQL (runtime) | 15-alpine (Docker image) | Read model storage and reporting queries |

R2DBC has no relationship-mapping annotations and no in-memory database equivalent to H2, which shapes several schema decisions — see `[[r2dbc-migration-gotchas]]` in `.claude/knowledge/`. MongoDB event store handles command-side durability; Postgres read projections are populated via Kafka event consumers and are eventual-consistent.

## Security

| Component | Version | Notes |
|---|---|---|
| Spring Security | via Boot BOM | Reactive chain (`ServerHttpSecurity`), stateless JWT auth |
| JJWT (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) | 0.12.7 | HMAC-SHA256 signed tokens; `parseSignedClaims()` only |
| BCrypt | via Spring Security | Password hashing, strength 10 |
| Resilience4j Rate Limiter | 2.4.0 | Brute-force protection on login/payment endpoints |

## Messaging

| Component | Version | Notes |
|---|---|---|
| Spring Kafka | 4.1.0 | Producer/consumer wiring, typed consumer factories; Boot 4 requires the `spring-boot-starter-kafka` starter — depending on `spring-kafka` alone no longer pulls in the autoconfiguration |
| Confluent Kafka | 7.4.0 (Docker image) | Single broker, 3 partitions per topic |
| Confluent ZooKeeper | 7.4.0 (Docker image) | Kafka coordination |

Topics: `payment-initiated`, `payment-charged`, `payment-completed`, `payment-failed`.

## Resilience

| Module | Version | Purpose |
|---|---|---|
| resilience4j-spring-boot4 | 2.4.0 | Autoconfiguration and property binding; 2.4.0 is the first release with Boot 4 support, and this artifact was initially missing from the resilience4j BOM, so declare it explicitly |
| resilience4j-circuitbreaker | 2.4.0 | Charger and notification service protection |
| resilience4j-retry | 2.4.0 | Transient-failure retries with exponential/randomized backoff |
| resilience4j-timelimiter | 2.4.0 | Timeout enforcement |
| resilience4j-ratelimiter | 2.4.0 | Per-user rate limiting |
| resilience4j-reactor | 2.4.0 | Lets the above annotations decorate `Mono`-returning methods directly, not just `CompletableFuture` |

## Observability

| Component | Version | Role |
|---|---|---|
| Micrometer | via Boot BOM | Metrics facade; `CustomMetrics` component for counters/timers/gauges |
| micrometer-registry-prometheus | via Boot BOM | Exposes `/actuator/prometheus`; version follows the Boot 4.1 BOM — do not re-pin the 1.15.x version that matched Boot 3.5 |
| micrometer-tracing-bridge-brave | via Boot BOM | Distributed tracing instrumentation; same BOM rule as above |
| zipkin-reporter / zipkin-sender-urlconnection / zipkin-reporter-brave | managed by Boot BOM | Span export pipeline — all three are required together, or spans are silently dropped (see `[[distributed-tracing-observability-gotchas]]`) |
| Prometheus | latest (Docker image) | Metrics storage, 15s scrape interval, UI at `:9090` |
| Jaeger (all-in-one) | latest (Docker image) | Trace storage and UI at `:16686`; Service Performance Monitor reads RED metrics from Prometheus, not from trace storage |
| OpenTelemetry Collector (contrib) | latest (Docker image) | Sits between the app and Jaeger; forwards spans unchanged and derives SPM metrics via the spanmetrics connector |
| Grafana | latest (Docker image) | Dashboards over Prometheus, Jaeger, and Loki; auto-provisioned and read-only from the UI, `:3000` |
| Loki | latest (Docker image) | Log aggregation, `:3100`; no UI of its own, queried through Grafana |
| loki-logback-appender | 2.0.3 | Ships structured JSON logs from the app to Loki |
| logstash-logback-encoder | 9.0 | JSON log encoding for console/file/Loki appenders |

The app talks to `otel-collector`, not Jaeger directly, so the same spans feed both the trace store and the Service Performance Monitor metrics pipeline.

## Testing

| Component | Version | Purpose |
|---|---|---|
| JUnit 5 (Jupiter) | via Boot BOM | Test framework |
| Mockito (`mockito-core`) | via Boot BOM | Comes with `spring-boot-starter-test`; attached as a java agent through the surefire `argLine` because JDK 21+ blocks dynamic self-attachment. The discontinued `mockito-inline` artifact must not be declared — inline mocking is the Mockito 5 default |
| reactor-test | via Boot BOM | `StepVerifier` — the reactive equivalent of a plain assertion |
| Testcontainers 2.x (`testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-mongodb`, `spring-boot-testcontainers`) | via Boot BOM (2.0.5) | Real Postgres and MongoDB per test run; R2DBC has no H2-equivalent in-memory option. Boot 4.1 manages Testcontainers 2.x, which renamed the module artifacts — the 1.x names (`junit-jupiter`, `postgresql`, `mongodb`) no longer resolve |
| ArchUnit (`archunit-junit5`) | 1.4.0 | Enforces ARCHITECTURE_RULES in `mvn verify` (layering, no field injection, no JPA, no blocking on reactive types) |
| spring-kafka-test | 4.1.0 | Embedded Kafka broker for isolated consumer/producer tests |
| JaCoCo | 0.8.12 | Java 23-compatible coverage; `mvn verify` enforces a 95% instruction-coverage minimum |

## Developer Tooling

| Component | Version | Purpose |
|---|---|---|
| Lombok | 1.18.38 | `@Data`, `@RequiredArgsConstructor`, `@Slf4j`; excluded from the packaged jar by the Spring Boot Maven plugin |

## Containerization & Deployment

**Dockerfile** — multi-stage build:
- Stage 1 (builder): `maven:3-amazoncorretto-23-alpine`, dependency resolution and packaging cached via a `~/.m2` mount
- Stage 2 (runtime): `amazoncorretto:23-alpine`, exposes `8080` (HTTP) and `5005` (remote debug), healthcheck against `/actuator/health`

**Docker Compose** — full local stack (`docker-compose up -d`):

| Service | Image | Port(s) |
|---|---|---|
| payment-core | built from `Dockerfile` | 8080, 5005 |
| postgres | postgres:15-alpine | 5432 |
| mongodb | mongo:7.0-alpine | 27017 |
| zookeeper | confluentinc/cp-zookeeper:7.4.0 | 2181 |
| kafka | confluentinc/cp-kafka:7.4.0 | 9092 |
| prometheus | prom/prometheus:latest | 9090 |
| jaeger | jaegertracing/all-in-one:latest | 16686 (UI), 9411, 14250, 14268, 14269 |
| otel-collector | otel/opentelemetry-collector-contrib:latest | 8889 |
| grafana | grafana/grafana:latest | 3000 |
| loki | grafana/loki:latest | 3100 |
| db-seed | postgres:15-alpine | — (one-shot seeding job) |

`k8s/` exists as a placeholder for Kubernetes manifests; no manifests are checked in yet.

## Load Testing

| Component | Purpose |
|---|---|
| K6 | JWT-authenticated load scenarios (`loadtest/payment-load-test.js`, `loadtest/scenarios/`) |

Scenarios cover a progressive-ramp full flow, a steady-state run, and a spike test; results can be exported to Prometheus via `k6-prometheus.yml`.
