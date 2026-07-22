---
id: T-001
title: Establish the integration test harness
status: in-progress
owner: backend-engineer
branch: task/T-001-test-harness
depends-on: []
---

# T-001: Establish the integration test harness

## Goal

A working baseline every later task inherits: Testcontainers for Postgres and MongoDB, embedded Kafka, and a passing `mvn verify` that exercises a trivial end-to-end context load.

## Scope

- `src/test/java/com/orvigas/support/` (shared test infrastructure)
- `src/test/resources/`
- `pom.xml` (test-scoped additions only, if anything is missing)

## Acceptance criteria

- [ ] A `@SpringBootTest` context-load test passes against Testcontainers Postgres and MongoDB
- [ ] Shared container configuration is reusable (`@ServiceConnection` or an abstract base), not copy-pasted per test
- [ ] Embedded Kafka test slice works via `spring-kafka-test`
- [ ] `mvn verify` passes locally and in CI
- [ ] Failure paths tested, not only the happy path (containers unavailable fails fast with a clear message)

## Notes

R2DBC and the Axon MongoDB extension both lack in-memory substitutes, which is why this task exists before any feature work. See `governance/TECH_STACK.md` testing section and `.claude/knowledge/spring-boot-4-upgrade.md`.

## Handoff log
