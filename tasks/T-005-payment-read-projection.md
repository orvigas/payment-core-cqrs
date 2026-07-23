---
id: T-005
title: Payment read projection via Kafka to Postgres
status: backlog
owner: none
branch: none
depends-on: [T-003, T-007]
---

# T-005: Payment read projection via Kafka to Postgres

## Goal

The first query-side projection: payment events published to Kafka after durable commit, consumed idempotently, and upserted into a Postgres read model via R2DBC, with the Flyway migration that creates the schema.

## Scope

- `src/main/java/com/orvigas/payment/projection/`
- `src/main/resources/db/migration/`
- `src/test/java/com/orvigas/payment/projection/`

## Acceptance criteria

- [ ] Events are published to Kafka only after the event-store commit is durable (verify the mechanism against the transaction manager in use; do not trust after-commit hooks blind)
- [ ] Consumer is idempotent: duplicate delivery and out-of-order delivery tested explicitly
- [ ] Read model uses portable column types with explicit constraints; no native Postgres enums
- [ ] Application-assigned keys handled with explicit new-vs-existing logic (no null-id heuristic)
- [ ] Migration is append-only and numbered correctly
- [ ] Testcontainers integration test covers event-to-row flow end to end
- [ ] `mvn verify` passes

## Notes

The three hardest rules in `governance/ARCHITECTURE_RULES.md` all land in this task (publish-after-commit, idempotent consumers, id heuristics). Read them before starting. Kafka topics are listed in `.claude/CLAUDE.md`.

Now depends on T-007 as well: no code publishes payment events to Kafka yet, so this task has nothing real to consume until T-007 lands.

## Handoff log
