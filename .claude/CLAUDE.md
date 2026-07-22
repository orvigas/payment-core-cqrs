# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project State

This is an **early-stage scaffold** for a Payment Core platform built on DDD, CQRS, Event Sourcing, and Saga patterns. The build and infrastructure skeleton exists: `pom.xml` (Axon + reactive MongoDB write layer, R2DBC read layer), `docker-compose.yml` with the full local stack, `application.yml`, the monitoring configs under `monitoring/`, and the application entry point. No domain, API, or persistence code has been written yet — `services/`, `shared/`, `architecture/`, `quality/`, `roles/`, `tasks/`, and `workflow/` are still empty and will be filled in as the project develops. `governance/TECH_STACK.md` is current and authoritative for versions; the domain model is documented in `knowledge/domain/`, and architecture decisions live in `knowledge/decisions/` (start with ADR-001 for the Axon/MongoDB event store).

`AGENTS.md` at the repo root mirrors this file for non-Claude tools (OpenCode and others). This file and `.claude/rules/` are canonical; keep `AGENTS.md` a thin pointer so the two cannot drift.

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

- **Java 21, Spring Boot 4.1.x, Maven.** Fully reactive: Spring WebFlux end to end (`Mono`/`Flux`, no JPA/Hibernate). The one intentional blocking exception is `@KafkaListener` methods, which run on Kafka consumer threads, not the Netty event loop.
- **CQRS split (see ADR-001):** Axon Framework 4.10.5 on the command side with a **MongoDB 7** event store (Axon MongoDB extension, reactive Spring Data MongoDB); **Postgres 15** on the query side via Spring Data R2DBC for read projections, populated by Kafka consumers and eventually consistent.
- **Postgres connection paths:** R2DBC for application traffic, JDBC solely for Flyway migrations (Flyway has no R2DBC support). Migrations cover the read schema only and live in `src/main/resources/db/migration`.
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
