---
id: T-003
title: Payment aggregate with commands and events
status: backlog
owner: none
branch: none
depends-on: [T-001, T-002]
---

# T-003: Payment aggregate with commands and events

## Goal

The event-sourced Payment aggregate in Axon: initiate, authorize, capture, complete, fail, expire, refund — with capture and refund entities and every invariant from the domain doc enforced.

## Scope

- `src/main/java/com/orvigas/payment/` (aggregate, commands, events)
- `src/test/java/com/orvigas/payment/`

## Acceptance criteria

- [ ] Commands and events are immutable records matching the contracts in `knowledge/domain/payment.md`, `capture.md`, and `refund.md`
- [ ] Invariants enforced and tested: `capturedAmount <= authorizedAmount`, `refundedAmount <= capturedAmount` (in-flight amounts counted), currency fixed at initiation, terminal-state protection
- [ ] Initiation is idempotent via the client idempotency key
- [ ] Axon test fixtures cover every command: acceptance and each rejection reason
- [ ] Command handlers validate; event-sourcing handlers only mutate state
- [ ] `mvn verify` passes

## Notes

Aggregate boundary rationale is in `knowledge/domain/payment.md`; captures and refunds are entities inside the aggregate, not separate aggregates. Event names map to the Kafka topics listed in `.claude/CLAUDE.md`.

## Handoff log
