# Axon Subscribing vs. Tracking Processor Durability Guarantee

## What was the problem?

T-007 needed to publish payment domain events to Kafka only after the event
is durably committed to the MongoDB event store — never before, and never
racing with it. `application.yml` already had a processing group named
`events` configured with `mode: subscribing`, which looked like the obvious
place to hang a Kafka-publishing `@EventHandler`. It isn't: subscribing
processors don't give the guarantee this task needs.

## What was the root cause?

Axon has two fundamentally different ways an `@EventHandler` component can
receive events, and they differ in exactly the dimension that matters here:

- **Subscribing Event Processor (SEP):** invoked synchronously, on the same
  thread that published the event, as part of the same unit of work that is
  persisting it. That's a deliberate feature — it lets a subscribing handler
  cause the whole transaction to roll back if it throws — but it also means
  a subscribing handler can run *before* the storage engine's write is
  actually durable, depending on how the unit of work's commit phases are
  ordered for the transaction manager in use. Trusting it as "the event is
  already saved" is exactly the "framework after-commit hook taken on faith"
  governance warns against.
- **Tracking Event Processor (TEP):** runs on its own thread, reading events
  back out of the event store via a `TokenStore`-tracked position. Its
  defining property: it is "always independent of the transaction that has
  published the event (this transaction must have been committed)". A TEP
  physically cannot see an event before the storage engine has durably
  written it, because it isn't invoked in-process at all — it polls the
  store.

The existing `events` processing group (subscribing) was pre-existing
placeholder config with no components actually registered on it. It would
have been the wrong home for this task's publisher regardless.

## How was it diagnosed?

Checked the Axon reference docs and source for `EmbeddedEventStore`,
`AbstractEventBus`, and the subscribing-vs-tracking processor pages
directly, rather than assuming subscribing processors were "close enough."
Confirmed via AxonIQ's own documentation, quoted above, that only tracking
processors have a documented after-persistence guarantee.

## What was the solution?

Give the Kafka publisher its own processing group, explicitly in tracking
mode, instead of reusing the existing `events` (subscribing) group:

```java
@Component
@ProcessingGroup("payment-kafka-publisher")
public class PaymentEventKafkaPublisher {
    @EventHandler
    public void on(PaymentInitiated event) { ... }
}
```

```yaml
axon:
  eventhandling:
    processors:
      payment-kafka-publisher:
        mode: tracking
```

The Mongo token store (`axon.mongo.token-store.enabled: true`) was already
enabled, which is the prerequisite a tracking processor needs to persist its
read position.

## Why does the solution work?

A tracking processor's event stream comes from the storage engine, not from
an in-process publish call. By construction, it cannot observe an event
before `appendEvents()` on the Mongo storage engine has returned
successfully. This makes the durability guarantee a structural property of
which processor type is used, not something that depends on guessing how a
particular transaction manager sequences commit callbacks.

## What are the trade-offs or limitations?

- Tracking processing is asynchronous relative to the command that raised
  the event: `commandGateway.sendAndWait()` returns as soon as the aggregate
  and event store have processed the command, before the Kafka publish has
  necessarily happened. That's the intended trade-off for this task (`no
  blocking calls introduced on the command-handling path`), not a bug — but
  it does mean "committed" and "published to Kafka" are not the same instant,
  and any consumer of the read side is eventually, not immediately,
  consistent with the write side (already true by design per ADR-001).
- A tracking processor with no stored token starts from the head of the
  entire event store by default (a full replay), not just new events. Fine
  for this codebase today since no other component writes real Payment
  events into Mongo outside of integration tests, but worth remembering if a
  test or environment ever needs a fresh processor to skip history.

## How can this issue be prevented?

Default to tracking mode for any new Axon event-handling component whose job
depends on "this only happened once the event was durably stored" (Kafka
bridges, any future outbox-style relay). Reach for subscribing mode only when
the handler genuinely needs to participate in the same transaction as the
aggregate (e.g., a saga step that must be able to abort the command).

## Which versions, libraries, or environments are affected?

- Axon Framework 4.10.4, `axon-mongo-spring-boot-autoconfigure`.
- Any Axon setup where `axon.eventhandling.processors.<default-group>.mode`
  is set to `subscribing` and a new handler is added without its own
  `@ProcessingGroup` — it silently inherits the wrong mode.

## Are there related issues or documentation?

- `knowledge/decisions/adr-001-axon-mongodb-event-store.md`
- `knowledge/decisions/adr-002-payment-kafka-topic-mapping.md`
- `tasks/T-007-payment-kafka-publisher.md`
- `src/main/java/com/orvigas/payment/publish/PaymentEventKafkaPublisher.java`

## What keywords would help someone find this entry later?

axon, tracking event processor, subscribing event processor, event store,
mongodb, durability, after-commit, unit of work, kafka publisher, token
store, eventual consistency
