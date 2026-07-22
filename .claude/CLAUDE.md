# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project State

This is a **greenfield scaffold** for a Payment Core platform built on DDD, CQRS, Event Sourcing, and Saga patterns. No application code, `pom.xml`, or `docker-compose.yml` exists yet — only the governance docs and empty directories (`architecture/`, `knowledge/`, `quality/`, `roles/`, `services/`, `shared/`, `tasks/`, `workflow/`) that will be filled in as the project develops. `governance/TECH_STACK.md` describes the target stack the implementation must converge on; treat references in it to files like `pom.xml` as the intended end state, not the current one.

## Governance Docs (read before implementing anything)

- `governance/MISSION.md` — project mission: maintainability, scalability, architectural consistency.
- `governance/VISION.md` — target end state and priority order (correctness > auditability > maintainability > scalability > speed); what the platform deliberately does not do.
- `governance/CODING_STANDARD.md` — how code is written: records/sealed types, reactive discipline (no `.block()`, no manual `subscribe()`), Lombok allowlist, Conventional Commits.
- `governance/QUALITY_GATE.md` — automated definition of done: 95% JaCoCo floor, static analysis, migration checks, API-contract diffing, review rules, time-boxed waivers only.
- `governance/SECURITY_POLICY.md` — JWT/key handling, authorization rules, data classification and logging redaction, supply-chain scanning, incident response.
- `governance/ARCHITECTURE_RULES.md` — binding engineering rules. Highlights that shape day-to-day decisions here:
  - Strict one-way layering (controller → service → repository); constructor injection, never field injection.
  - Publish events only after the state change is durably committed; don't trust framework "after commit" hooks blind — verify compatibility with the transaction manager in use.
  - Consumers must be idempotent; events are immutable, versioned, typed records.
  - Once committed to the reactive/non-blocking model, no blocking calls on the request path; unavoidable blocking work goes to a dedicated thread pool.
  - Application-assigned primary keys break the persistence layer's null-ID "is new" heuristic — model new-vs-existing explicitly.
  - Prefer portable column types + explicit constraints over vendor-native types (e.g., no native Postgres enums) unless the data-access layer verifiably supports them.
  - Migrations are append-only and never edited after being applied.
- `governance/TECH_STACK.md` — pinned versions and infrastructure layout for every component.

## Target Stack (from TECH_STACK.md)

- **Java 21, Spring Boot 4.1.x, Maven.** Fully reactive: Spring WebFlux + Spring Data R2DBC (`Mono`/`Flux` end to end, no JPA/Hibernate). The one intentional blocking exception is `@KafkaListener` methods, which run on Kafka consumer threads, not the Netty event loop.
- **Postgres 15** with two connection paths: R2DBC for application traffic, JDBC solely for Flyway migrations (Flyway has no R2DBC support). Migrations live in `src/main/resources/db/migration`.
- **Kafka** (Spring Kafka) with topics `payment-initiated`, `payment-charged`, `payment-completed`, `payment-failed`.
- **Security:** stateless JWT (JJWT, HMAC-SHA256, `parseSignedClaims()` only), BCrypt, Resilience4j rate limiting on login/payment endpoints. Reactive security chain (`ServerHttpSecurity`) — auth context must propagate via Reactor context, not thread-locals.
- **Resilience4j** (circuit breaker, retry, time limiter, rate limiter) with `resilience4j-reactor` so annotations decorate `Mono`-returning methods.
- **Observability:** Micrometer → Prometheus, Brave/Zipkin tracing exported through an OpenTelemetry Collector to Jaeger (the app talks to `otel-collector`, never Jaeger directly), Loki + Grafana for logs/dashboards. All three zipkin-reporter artifacts are required together or spans are silently dropped.
- **OpenAPI:** SpringDoc `springdoc-openapi-starter-webflux-ui` (the WebMVC artifact is incompatible). Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`.
- **Lombok** for boilerplate (`@Data`, `@RequiredArgsConstructor`, `@Slf4j`).

## Commands (once the Maven project exists)

- Build + test + coverage gate: `mvn verify` — JaCoCo enforces a **95% instruction-coverage minimum**.
- Run a single test class: `mvn test -Dtest=ClassName` (add `#methodName` for one method).
- Full local stack: `docker-compose up -d` (app :8080, Postgres :5432, Kafka :9092, Prometheus :9090, Jaeger UI :16686, Grafana :3000, Loki :3100).
- Load tests: K6 scenarios in `loadtest/` (`loadtest/payment-load-test.js`, `loadtest/scenarios/`).

## Testing Conventions

- Reactive assertions use `StepVerifier` (reactor-test), not plain assertions on blocked results.
- Persistence tests run against real Postgres via Testcontainers — R2DBC has no H2-style in-memory option, and the schema depends on Postgres-specific behavior. Kafka consumer/producer tests use `spring-kafka-test` embedded broker.
- Test failure paths explicitly; coverage is a floor, not a target.
