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

**Review 2026-07-23 (code-reviewer): request changes.**

Blocker — Kafka publish failures are logged-and-ignored on a money path, with no metrics and no recovery path:

- `PaymentEventKafkaPublisher.publish()`'s `whenComplete` callback only does `log.error(...)` on failure. `governance/CODING_STANDARD.md` §4 names this pattern directly as a review blocker: "a logged-then-ignored failure in a money path is a review blocker."
- No Micrometer counter/timer exists anywhere in the new code. `governance/ARCHITECTURE_RULES.md` §9 and `governance/QUALITY_GATE.md` §8 (Observability Readiness) require outcome counters and latency timers on new operations as a hard merge gate, not a follow-up.
- Because the `@EventHandler` method returns normally regardless of the async send's outcome, the tracking processor's token advances unconditionally — a publish that exhausts producer retries is lost with nothing to alert on it beyond an `ERROR` log line. The "worth paging on" note in this log doesn't correspond to anything that actually pages.

Fix required before merge: add a Micrometer counter (success/failure, tagged by event type/topic) around `publish()`, and document/implement how an operator notices and recovers from an exhausted-retry failure.

Should-fix: state explicitly in `adr-002-payment-kafka-topic-mapping.md` and `PaymentKafkaEvent`'s javadoc that downstream consumers (T-005) must be idempotent against redelivery, since a tracking-processor crash between a successful send and the token commit can redeliver an event.

Nits: `PaymentKafkaEvent.eventType()` javadoc says none of the three interface methods are canonical record components but only two carry `@JsonProperty` — clarify that `topic()` deliberately doesn't need it (routing metadata, not wire payload). `PaymentEventKafkaPublisherIntegrationTest`'s `readValue` helper catches bare `Exception`, which `governance/CODING_STANDARD.md` §4 bans repo-wide, tests included.

Security review (no blocking findings — Medium and below, per `governance/QUALITY_GATE.md` triaged in review rather than auto-blocking):

- Medium: `RefundRequestedPayload.reasonNotes` copies a free-text field verbatim onto a permanent, unredactable Kafka topic (`payment-refunded`), which conflicts with `governance/SECURITY_POLICY.md` §5's rule against raw personal data in event payloads given retention/replay makes deletion impractical. Every other payload in this PR only carries structured identifiers/enums/amounts. Recommend dropping `reasonNotes` from the wire payload (keep the bounded `reasonCode` enum only) or adding a length cap and an explicit no-PII policy enforced before the refund request reaches the aggregate.
- Low: this PR is what actually activates the first tracking event processor, which exercises Axon's Mongo token store's XStream-based serializer for the first time — XStream has a known CVE history for reflection-based deserialization. Not introduced by this PR's own code, but worth a follow-up ticket on whether Axon's Mongo extension can use Jackson instead (already used elsewhere in this app) for the token store.
- Low: confirm `FailureReason.message()` (used in `PaymentFailedPayload`/`CaptureFailedPayload`/`RefundFailedPayload`/`PaymentAuthorizedPayload`) is always populated from curated, provider-agnostic strings, never raw provider API response text, before it goes out on a permanent topic.

PR #7 stays open; fixes land as new commits on this branch.

**Fix round 2026-07-23 (backend-engineer): addressed review findings.**

1. **Blocker — observability, fixed.** `PaymentEventKafkaPublisher.publish()` now records both a `payment.kafka.publish.total` `Counter` and a `payment.kafka.publish` `Timer`, tagged by `eventType`, `topic`, and `outcome` (`success`/`failure`), on every send outcome — `MeterRegistry` is now a constructor dependency alongside `KafkaTemplate`. This closes the actual gap the reviewer flagged, not just the letter of "add a metric": the tracking processor's token still advances unconditionally once `publish()` returns (that part is unchanged and is inherent to the fire-and-forget design), but an operator now has something concrete to alert on — a Prometheus rule on `increase(payment_kafka_publish_total{outcome="failure"}[5m]) > 0` catches an exhausted-retry loss within minutes instead of relying on someone reading an `ERROR` log line. Judged that this, combined with the event store remaining the durable source of truth for a manual replay, is enough for now; a dead-letter topic or automatic replay trigger is a reasonable follow-up once T-005 exists and there's a real consumer to replay into, not before. Documented in `PaymentEventKafkaPublisher`'s class Javadoc and covered by two new tests (`PaymentEventKafkaPublisherTest#testSuccessfulPublishRecordsSuccessMetric`, and the existing failure-path test extended to assert the `outcome="failure"` counter increments).
2. **Should-fix — idempotent consumers, done.** Added an explicit paragraph to `PaymentKafkaEvent`'s Javadoc and a new "Idempotent consumption" section to `adr-002-payment-kafka-topic-mapping.md` stating that a tracking-processor crash between a successful send and the token commit redelivers the event, so every consumer (T-005 first) must treat replaying the same payload as a safe no-op.
3. **Nits, both fixed.** `PaymentKafkaEvent.topic()`'s Javadoc now says explicitly it's routing metadata, not wire payload, and deliberately carries no `@JsonProperty`; `eventType()`/`schemaVersion()`'s Javadoc now says which two methods actually need the annotation and why. `PaymentEventKafkaPublisherIntegrationTest#readValue` now catches `JsonProcessingException` instead of a bare `Exception`.
4. **Security, medium, fixed.** Dropped `reasonNotes` entirely from `RefundRequestedPayload` (record component, constructor, and `from()` mapping) — only the bounded `reasonCode` enum goes out on `payment-refunded` now. Added a Javadoc note on the class explaining why, and a regression test (`PaymentKafkaEventPayloadTest#testRefundRequestedPayloadNeverExposesReasonNotes`) that asserts the record's components never include `reasonNotes`/`notes`, so a future change can't silently reintroduce it without deliberately deciding to.
5. **Security, low, addressed as suggested (not blocking).** Added a paragraph to `adr-002-payment-kafka-topic-mapping.md`'s Consequences noting this PR activates the first Axon tracking processor and therefore the first real use of the Mongo token store's XStream serializer, with a follow-up-ticket note about whether Axon's Mongo extension can swap it for Jackson instead; not fixing it here since the token bytes are only ever produced and consumed by this application. Also added a Javadoc note to `FailureReason` stating `message` must be a curated, provider-agnostic string (not raw provider response text) given it now reaches three permanent Kafka topics — a documentation fix since no actual provider integration exists yet to have gotten this wrong.

`mvn verify` passes after all fixes: 143 tests (2 more than the previous round, from the new metric-assertion test and the reasonNotes regression test), 0 failures, JaCoCo 95% floor met, ArchUnit clean.
