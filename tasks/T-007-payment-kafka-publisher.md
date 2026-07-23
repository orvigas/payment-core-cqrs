---
id: T-007
title: Kafka event publisher for payment domain events
status: backlog
owner: none
branch: none
depends-on: [T-003]
---

# T-007: Kafka event publisher for payment domain events

## Goal

Every event the `Payment` aggregate emits is published to its Kafka topic only after the Axon event store commit is durably recorded — closing the write-side half of CQRS so a read-side consumer (T-005) has something real to project.

## Scope

- `src/main/java/com/orvigas/payment/publish/`
- `src/test/java/com/orvigas/payment/publish/`

## Acceptance criteria

- [ ] Publish happens after the MongoDB event store commit is durable, not on an Axon "after commit" hook taken on faith — verify the actual guarantee the Mongo event store + unit-of-work gives here and document the finding (`governance/ARCHITECTURE_RULES.md`)
- [ ] Topic mapping for every event type the aggregate currently emits is resolved and documented. `CLAUDE.md` only names four topics (`payment-initiated`, `payment-charged`, `payment-completed`, `payment-failed`) but the aggregate emits more (`PaymentAuthorized`, `PaymentExpired`, `CaptureSucceeded`/`CaptureFailed`, `RefundRequested`/`RefundSucceeded`/`RefundFailed`) — decide the mapping (some may share a topic, keyed/typed by an event-type field) and record the decision in this file's handoff log; open an ADR if the mapping isn't a trivial extension of the documented four
- [ ] Publisher failure does not roll back or reattempt the aggregate command — the event store is the source of truth and is already durable by the time publish is attempted; failed publishes are retried independently (document the retry/backlog strategy, whether that's producer retry config or an outbox-style follow-up)
- [ ] No blocking calls introduced on the command-handling path
- [ ] Published payloads are immutable, versioned records (matches `governance/ARCHITECTURE_RULES.md`), not raw Axon event wrappers
- [ ] Integration test using the embedded Kafka broker (build on the pattern in `EmbeddedKafkaRoundTripTest`) proves a committed command results in the correct topic receiving the correct payload
- [ ] `mvn verify` passes

## Notes

`src/test/java/com/orvigas/support/kafka/EmbeddedKafkaRoundTripTest.java` already proves the embedded-broker test harness works and says explicitly that payment event topic tests build on top of it — start there rather than re-deriving broker setup.

No Kafka producer code exists anywhere in `src/main` yet (`KafkaTemplate`, `@EventHandler` publishing bridges, and topic config are all absent) — this task is greenfield, not a fix to existing wiring.

T-005 (read projection) depends on this task: it has nothing to consume until events actually reach Kafka.

## Handoff log
