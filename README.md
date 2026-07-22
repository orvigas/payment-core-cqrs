# Payment Core

A CQRS and event-sourced payment processing platform, built on DDD and Saga patterns, with correctness and auditability as non-negotiable priorities.

## Why this exists

Payment Core is the system of record for the payment lifecycle: initiation, authorization, capture, completion, refund, and settlement. Money movement demands a level of correctness and auditability that CRUD over a mutable row cannot provide — every state change is an immutable event, and the full history of any payment is reconstructable from the event log. See `governance/VISION.md` for the full reasoning and the priority order everything else is weighed against (correctness > auditability > maintainability > scalability > delivery speed).

## Project status

Early-stage. The build, infrastructure, governance, domain model, and multi-agent tooling are in place; no domain or API code has been written yet. `mvn verify` passes against the empty skeleton. See `tasks/TASKS.md` for what's next — T-001 (test harness) is the first task to pick up.

## Architecture

Payment Core splits commands and queries onto physically separate stores (ADR-001):

```
Command side (write)                    Query side (read)
─────────────────────                   ─────────────────
Axon Framework                          Spring Data R2DBC
  aggregates, commands, events    ──▶     read projections
MongoDB (event store,                   PostgreSQL 15
  snapshots, tracking tokens)              (read models only)
        │
        └──────────▶ Kafka ──────────▶ projection consumers
                (payment-initiated, payment-charged,
                 payment-completed, payment-failed)
```

- **Write side**: Axon Framework handles command dispatch, aggregate lifecycle, and event sourcing. The event store, snapshots, and tracking tokens live in MongoDB — an append-only log fits that access pattern better than a relational schema, and it keeps the write/read boundary physically enforced rather than just conventional.
- **Read side**: Postgres holds only projections, populated asynchronously by Kafka consumers. Read models are eventually consistent and disposable — losing one is an inconvenience, since it can be rebuilt by replaying the event log; losing the log is unacceptable.
- **Messaging**: Kafka is the bridge between the two sides. Topics: `payment-initiated`, `payment-charged`, `payment-completed`, `payment-failed`.
- **Reactive end to end**: Spring WebFlux and R2DBC, no JPA/Hibernate, no `.block()`. The one deliberate exception is `@KafkaListener` methods, which run on Kafka consumer threads rather than the Netty event loop.

Why this split instead of hand-rolled event sourcing or Axon-over-Postgres: `knowledge/decisions/adr-001-axon-mongodb-event-store.md`.

## Domain model

The core domain objects and the invariants each one enforces are documented in `knowledge/domain/`:

| Object | Role |
|---|---|
| [`payment.md`](knowledge/domain/payment.md) | Aggregate root: initiate → authorize → capture → complete/fail lifecycle |
| [`capture.md`](knowledge/domain/capture.md) | Entity inside Payment: claims authorized funds, full or partial |
| [`refund.md`](knowledge/domain/refund.md) | Entity inside Payment: returns captured funds, never uncaptured ones |
| [`settlement.md`](knowledge/domain/settlement.md) | Separate aggregate: periodic merchant payout batch |
| [`merchant.md`](knowledge/domain/merchant.md) | Separate aggregate: onboarding, fees, payout configuration |

## Tech stack

Full pinned versions and rationale: `governance/TECH_STACK.md`. Highlights:

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.x, Maven |
| Web | Spring WebFlux (Netty, non-blocking) |
| Command side | Axon Framework 4.10.4, reactive MongoDB |
| Query side | Spring Data R2DBC, PostgreSQL 15 |
| Migrations | Flyway (JDBC-only; read schema, append-only) |
| Messaging | Spring Kafka |
| Security | Stateless JWT (JJWT, HMAC-SHA256), BCrypt, Resilience4j rate limiting |
| Resilience | Resilience4j (circuit breaker, retry, time limiter, rate limiter) |
| Observability | Micrometer → Prometheus, Brave/Zipkin → OpenTelemetry Collector → Jaeger, Loki + Grafana |
| API docs | SpringDoc (`/swagger-ui.html`, `/v3/api-docs`) |
| Testing | JUnit 5, StepVerifier, Testcontainers (Postgres + MongoDB), embedded Kafka, Axon test fixtures, ArchUnit |

## Getting started

Prerequisites: JDK 21, Maven, Docker (for Testcontainers and the local stack).

```bash
# Build, run tests, and enforce the quality gate (ArchUnit rules + 95% coverage floor)
mvn verify

# Run one test class (add #methodName for a single test)
mvn test -Dtest=ClassName

# Full local stack: app + Postgres + MongoDB + Kafka + observability
docker-compose up -d
```

Local stack endpoints once running:

| Service | URL |
|---|---|
| App | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Postgres | localhost:5432 |
| MongoDB | localhost:27017 |
| Kafka | localhost:9092 |
| Prometheus | http://localhost:9090 |
| Jaeger UI | http://localhost:16686 |
| Grafana | http://localhost:3000 |
| Loki | http://localhost:3100 |

Load tests: K6 scenarios are planned under `loadtest/` (not yet created — see `governance/TECH_STACK.md`).

## Repository layout

```
governance/     Binding project law: mission, vision, coding standard, quality gate, security policy, tech stack
knowledge/      Reference material: domain model, architecture decisions (ADRs), business context, glossary, FAQ
architecture/   Design docs for services and cross-cutting concerns
roles/          Pointer to .claude/agents/ — see "Multi-agent development" below
tasks/          Task ledger: backlog, ownership, scope, acceptance criteria
workflow/       The development workflow state machine
services/       Application code, organized per bounded context (empty until T-003 lands)
shared/         Cross-cutting domain primitives: Money, typed identifiers (empty until T-002 lands)
src/            Maven source root (main + test)
monitoring/     Prometheus, Loki, and OpenTelemetry Collector configuration
.claude/        Claude Code configuration: rules, agents, commands, hooks, knowledge, memory
```

## Contributing and quality gate

Every change must pass `mvn verify`, which runs:

- The full test suite (JUnit 5, StepVerifier for reactive code, Testcontainers for persistence, embedded Kafka for messaging)
- ArchUnit checks for the rules in `governance/ARCHITECTURE_RULES.md` (one-way layering, constructor injection only, no JPA/Hibernate, no blocking calls on reactive types, no thread-local security context)
- A 95% JaCoCo instruction-coverage floor

Full definition of done: `governance/QUALITY_GATE.md`. Coding conventions: `governance/CODING_STANDARD.md`. Security requirements (JWT handling, data classification, logging redaction): `governance/SECURITY_POLICY.md`.

Commits follow Conventional Commits.

## Multi-agent development

This repository is built to be worked on by multiple coding agents (Claude Code, OpenCode, or others) under one shared set of rules:

- `.opencode/agents/AGENTS.md` points any AGENTS.md-aware tool at the canonical instructions in `.claude/CLAUDE.md` and `.claude/rules/`. `.opencode/opencode.json` wires the same instructions into OpenCode.
- `.claude/agents/` defines five roles as invocable subagents: `architect`, `backend-engineer`, `test-engineer`, `code-reviewer`, `security-reviewer`.
- `tasks/` is a claim-based ledger — one task, one owner, one branch — so parallel agents don't collide. `workflow/DEVELOPMENT_WORKFLOW.md` defines the backlog → in-progress → review → done lifecycle.
- Slash commands `/new-task`, `/implement`, `/review`, and `/adr` make that workflow invocable rather than just documented.
- A PostToolUse hook (`.claude/hooks/check-java-standards.sh`) flags banned patterns — `.block()`, manual `subscribe()`, field injection, JPA imports, console output — the moment a file is edited, before they ever reach review.

Start here: `tasks/TASKS.md` for what's available to pick up, `workflow/DEVELOPMENT_WORKFLOW.md` for how work moves through the system.
