# ADR-001: Axon Framework with MongoDB event store and Postgres read models

- Status: accepted
- Date: 2026-07-22
- Deciders: orvigas

## Context

The platform is committed to CQRS and event sourcing (see `governance/VISION.md`: correctness and auditability rank above everything else). Two questions had to be settled before writing domain code: whether to hand-roll the event-sourcing machinery or adopt a framework, and where the event store lives. The realistic options were:

1. Hand-rolled event sourcing over Postgres (single database, full control, significant infrastructure code to write and test).
2. Axon Framework with its Postgres/JPA event store (framework support, but pulls JPA back into a stack that deliberately excludes it).
3. Axon Framework with the MongoDB extension as event store, keeping Postgres purely for read projections.

## Decision

Option 3. Axon Framework 4.10.4 handles command dispatch, aggregate lifecycle, and event sourcing. The event store, snapshots, and tracking tokens live in MongoDB 7 (Axon MongoDB extension, reactive Spring Data MongoDB driver). Postgres 15 via R2DBC holds only read projections, populated by Kafka consumers. Kafka remains the bridge between the write and read sides.

## Rationale

- Hand-rolling event sourcing (option 1) means writing and proving the hardest 20% ourselves: optimistic concurrency on the event stream, snapshotting, upcasting, replay. That code earns nothing product-wise and is exactly where correctness bugs hide.
- Axon over JPA/Postgres (option 2) would reintroduce Hibernate into a stack whose coding standard bans it, and mixes the append-heavy event log with relational read traffic in one database.
- The MongoDB event store fits an append-only event log well (fast inserts, schema-less event payloads) and gives a hard physical separation between write and read sides, which keeps the CQRS boundary honest.

## Consequences

- Two databases to operate (MongoDB + Postgres) plus Kafka: more moving parts locally and in production; docker-compose carries the full set.
- Read models are eventually consistent by construction; anything needing read-your-writes must query the command side or wait on the projection.
- Flyway migrations cover the read schema only; the event store is schema-less but needs deliberate indexing (aggregate id + sequence number).
- Axon 4.10.x is paired here with Spring Boot 4.1.x; the versions must move together. The 4.10.5 pin recorded earlier never existed on Maven Central; 4.10.4 is the last release of that line.
- Testcontainers must cover both MongoDB and Postgres; there is no in-memory substitute for either path.

## References

- `governance/TECH_STACK.md` — pinned versions and container layout.
- `.claude/knowledge/spring-boot-4-upgrade.md` — the coordinated Boot 4.1 dependency bumps this decision depends on.
- `knowledge/domain/payment.md` — the aggregate design that assumes this event-sourcing substrate.
