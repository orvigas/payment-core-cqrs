---
id: T-007
title: Kafka event publisher for payment domain events
status: review
owner: backend-engineer
branch: task/T-007-payment-kafka-publisher
depends-on: [T-003]
---

# T-007: Kafka event publisher for payment domain events

## Goal

Every event the `Payment` aggregate emits is published to its Kafka topic only after the Axon event store commit is durably recorded — closing the write-side half of CQRS so a read-side consumer (T-005) has something real to project.

## Scope

- `src/main/java/com/orvigas/payment/publish/`
- `src/test/java/com/orvigas/payment/publish/`

## Acceptance criteria

- [x] Publish happens after the MongoDB event store commit is durable, not on an Axon "after commit" hook taken on faith — verify the actual guarantee the Mongo event store + unit-of-work gives here and document the finding (`governance/ARCHITECTURE_RULES.md`)
- [x] Topic mapping for every event type the aggregate currently emits is resolved and documented. `CLAUDE.md` only names four topics (`payment-initiated`, `payment-charged`, `payment-completed`, `payment-failed`) but the aggregate emits more (`PaymentAuthorized`, `PaymentExpired`, `CaptureSucceeded`/`CaptureFailed`, `RefundRequested`/`RefundSucceeded`/`RefundFailed`) — decide the mapping (some may share a topic, keyed/typed by an event-type field) and record the decision in this file's handoff log; open an ADR if the mapping isn't a trivial extension of the documented four
- [x] Publisher failure does not roll back or reattempt the aggregate command — the event store is the source of truth and is already durable by the time publish is attempted; failed publishes are retried independently (document the retry/backlog strategy, whether that's producer retry config or an outbox-style follow-up)
- [x] No blocking calls introduced on the command-handling path
- [x] Published payloads are immutable, versioned records (matches `governance/ARCHITECTURE_RULES.md`), not raw Axon event wrappers
- [x] Integration test using the embedded Kafka broker (build on the pattern in `EmbeddedKafkaRoundTripTest`) proves a committed command results in the correct topic receiving the correct payload
- [x] `mvn verify` passes

## Notes

`src/test/java/com/orvigas/support/kafka/EmbeddedKafkaRoundTripTest.java` already proves the embedded-broker test harness works and says explicitly that payment event topic tests build on top of it — start there rather than re-deriving broker setup.

No Kafka producer code exists anywhere in `src/main` yet (`KafkaTemplate`, `@EventHandler` publishing bridges, and topic config are all absent) — this task is greenfield, not a fix to existing wiring.

T-005 (read projection) depends on this task: it has nothing to consume until events actually reach Kafka.

## Handoff log

**Completed 2026-07-22:**

Built the write-side-to-Kafka bridge in `com.orvigas.payment.publish`:

- `PaymentEventKafkaPublisher` — an `@ProcessingGroup("payment-kafka-publisher")` component with one `@EventHandler` method per domain event type, each translating the event into its Kafka payload and sending it via `KafkaTemplate<String, Object>`, keyed by payment id.
- Twelve payload records (`PaymentInitiatedPayload`, `PaymentAuthorizedPayload`, `PaymentChargedPayload`, `CaptureSucceededPayload`, `CaptureFailedPayload`, `PaymentCompletedPayload`, `PaymentFailedPayload`, `PaymentExpiredPayload`, `RefundRequestedPayload`, `RefundPendingPayload`, `RefundSucceededPayload`, `RefundFailedPayload`), all implementing a sealed `PaymentKafkaEvent` interface (`topic()`, `paymentId()`, `eventType()`, `schemaVersion()`). Each is built from its domain event via a static `from(...)` factory and carries only primitive/string fields — no Axon `EventMessage`, no domain value objects on the wire.
- `PaymentKafkaTopics` — the topic name constants.

**Durability finding (governance/ARCHITECTURE_RULES.md §7):** the existing `axon.eventhandling.processors.events.mode: subscribing` config (pre-dating this task, never previously exercised - no `@EventHandler` beans existed yet) does *not* give the guarantee this task needs. A Subscribing Event Processor runs in-process, on the same thread and unit of work that's committing the event - by design, so it can participate in the same transaction - which means it isn't guaranteed to run strictly after the Mongo append is durable. A Tracking Event Processor reads events back out of the event store on its own thread and is "always independent of the transaction that has published the event (this transaction must have been committed)" per Axon's own docs. `PaymentEventKafkaPublisher` therefore runs on its own dedicated processing group, `payment-kafka-publisher`, explicitly configured to `mode: tracking` in both `application.yml` and `test-harness-application.yml`, rather than reusing the `events` group. Full writeup: `.claude/knowledge/axon-subscribing-vs-tracking-durability.md`.

**Topic mapping decision:** documented in `knowledge/decisions/adr-002-payment-kafka-topic-mapping.md`. Summary: `payment-initiated` (`PaymentInitiated`), `payment-charged` (`PaymentAuthorized`, `PaymentCharged`, `CaptureSucceeded`, `CaptureFailed` - the whole authorize-through-capture phase), `payment-completed` (`PaymentCompleted`), `payment-failed` (`PaymentFailed`, `PaymentExpired`), and a new topic `payment-refunded` (`RefundRequested`, `RefundPending`, `RefundSucceeded`, `RefundFailed`) since the refund sub-lifecycle is a distinct, potentially-recurring business process that doesn't fit any of the other four. Every payload carries an `eventType` string so a topic with more than one shape stays consumable. `governance/TECH_STACK.md` and `.claude/CLAUDE.md` updated to list all five topics.

**Retry/durability strategy for the publish step itself:** fire-and-forget, not blocking. `KafkaTemplate.send()` returns a `CompletableFuture`; the publisher attaches a `whenComplete` callback that logs at ERROR on final failure and does nothing on success beyond a DEBUG log - it never blocks the tracking-processor thread waiting on the result. Reliability comes from producer configuration in `application.yml`: `enable.idempotence: true` (no duplicate/reordered records across retries within a partition), `retries: 2147483647` bounded by `delivery.timeout.ms: 120000` (retries for up to two minutes, then gives up), and `acks: all`. No outbox table: the Mongo event store is already the durable source of truth, and a publish that survives two minutes of producer retries and still fails is a broker outage worth paging on, not something to paper over with more application-level machinery. The residual gap (an outage longer than the delivery timeout) would need a manual replay from the event store - out of scope here, noted as follow-up if T-005 turns up a real need for it.

**No blocking on the command path:** the publisher only ever runs on the `payment-kafka-publisher` tracking processor's own thread, which is scheduled entirely independently of `commandGateway.sendAndWait()` returning - by the time an event reaches the publisher, the command that raised it has already completed. `KafkaTemplate.send()` itself can block briefly on first use per topic while the producer resolves metadata (an inherent characteristic of the Kafka client, not something this task's code controls), but that happens on the tracking-processor thread, never the WebFlux/command-handling path.

**Bugs found and fixed along the way (all pre-existing, never previously exercised - this task's integration test is the first real multi-command round trip against the real Mongo event store and idempotency repository):**

- `Money.isPositive()`/`isZero()` were being picked up by Jackson's bean-property introspection as extra JSON fields (`positive`, `zero`) alongside the two canonical record components, so any event containing a `Money` failed to deserialize on aggregate reload with `UnrecognizedPropertyException`. Fixed with `@JsonIgnore` on both methods.
- `spring.data.mongodb.uuid-representation` doesn't exist in Spring Boot 4.0+ (deprecated at error level, replaced by `spring.mongodb.representation.uuid`); without either being set correctly, any document containing a raw `UUID` (every typed id in `shared.id`) fails to encode. Added `spring.mongodb.representation.uuid: standard` to both YAML files.
- Axon's Mongo token store serializes tracking tokens with XStream (the "general" serializer), and XStream's reflection-based converter can't reach `java.util` internals under the Java 21+ module system without `--add-opens java.base/java.util=ALL-UNNAMED`. Added to the Surefire `argLine` in `pom.xml` (tests) and via `JDK_JAVA_OPTIONS` in `docker-compose.yml` (the running app - the `java` launcher picks this env var up automatically, no `Dockerfile` change needed).
- `PaymentKafkaEvent.eventType()`/`schemaVersion()` initially had no serialized representation at all: they're not canonical record components and don't follow Jackson's `getXxx`/`isXxx` bean-getter naming, so Jackson silently dropped them from every payload rather than failing loudly. Fixed with `@JsonProperty` on the interface methods (Jackson honors annotations declared on an implemented interface's methods).

**Touched outside the declared `payment/publish/` scope (flagged per workflow):**

- `src/main/resources/application.yml` - Kafka producer reliability config, the `payment-kafka-publisher` tracking processor entry, `spring.mongodb.representation.uuid`.
- `src/test/resources/test-harness-application.yml` - mirrors the above for the integration test context (it replaces `application.yml` wholesale, so nothing is inherited).
- `pom.xml` - Surefire `--add-opens` flag.
- `docker-compose.yml` - `JDK_JAVA_OPTIONS` for the same `--add-opens` requirement in the running container.
- `src/main/java/com/orvigas/shared/money/Money.java` - `@JsonIgnore` on `isPositive()`/`isZero()`.
- `governance/TECH_STACK.md`, `.claude/CLAUDE.md` - updated Kafka topic list to include `payment-refunded`.
- `knowledge/decisions/adr-002-payment-kafka-topic-mapping.md` (new), `.claude/knowledge/axon-subscribing-vs-tracking-durability.md` (new), `.claude/memory/MEMORY.md`, `.claude/history/2026-07.md` - per the project-knowledge rule.

**Verification:** `mvn verify` passes - 141 tests, 0 failures, JaCoCo 95% floor met, ArchUnit rules pass. New tests: `PaymentKafkaEventPayloadTest` (15 cases, every payload's `from()` mapping), `PaymentEventKafkaPublisherTest` (7 cases, mocked `KafkaTemplate`, including the publish-failure-doesn't-propagate path), `PaymentEventKafkaPublisherIntegrationTest` (3 cases, real Axon + Testcontainers Mongo + embedded Kafka broker, proving the full command-to-topic path for the success flow, the failure flow, and the new refund topic).

**What remains:** T-005 (read projection) can now consume from all five topics.
