# ADR-002: Kafka topic mapping for payment domain events

- Status: accepted
- Date: 2026-07-22
- Deciders: orvigas

## Context

T-007 needed to wire the `Payment` aggregate's domain events onto Kafka so the
read projection (T-005) has something to consume. `governance/TECH_STACK.md`
names four topics — `payment-initiated`, `payment-charged`,
`payment-completed`, `payment-failed` — but the aggregate has grown to emit
twelve event types since that document was written: `PaymentInitiated`,
`PaymentAuthorized`, `PaymentCharged`, `CaptureSucceeded`, `CaptureFailed`,
`PaymentCompleted`, `PaymentFailed`, `PaymentExpired`, `RefundRequested`,
`RefundPending`, `RefundSucceeded`, `RefundFailed`. The four documented topics
don't have an obvious slot for authorization, capture confirmation, expiry, or
any part of the refund sub-lifecycle. A mapping had to be decided before any
Kafka producer code could be written.

## Decision

Keep the four documented topics for the events they already name outright,
extend `payment-charged` to cover the authorize-through-capture phase, fold
expiry into `payment-failed`, and add one new topic, `payment-refunded`, for
the refund sub-lifecycle:

- `payment-initiated` — `PaymentInitiated`
- `payment-charged` — `PaymentAuthorized`, `PaymentCharged`, `CaptureSucceeded`, `CaptureFailed`
- `payment-completed` — `PaymentCompleted`
- `payment-failed` — `PaymentFailed`, `PaymentExpired`
- `payment-refunded` (new) — `RefundRequested`, `RefundPending`, `RefundSucceeded`, `RefundFailed`

Every published payload carries an explicit `eventType` discriminator and a
`schemaVersion` field, so a topic with more than one event type still lets a
consumer switch on the exact shape it received rather than having to guess
from context.

## Rationale

Three options were on the table:

1. **One topic per event type (twelve topics).** Rejected — it mirrors the
   event store's granularity but turns every new event type the aggregate
   ever adds into a topic-provisioning and consumer-wiring change. For a
   platform this early, that's premature infrastructure sprawl for a
   distinction consumers don't need yet: nothing downstream currently cares
   about `PaymentAuthorized` separately from `CaptureSucceeded`.
2. **Force everything onto the four documented topics only**, e.g. push
   refund events onto `payment-completed` or `payment-failed` because those
   are the nearest terminal states. Rejected — a refund succeeding is not the
   same fact as a payment completing, and a consumer subscribed to
   `payment-completed` to build a "money received" view would have to filter
   out refund noise it never asked for. Overloading a topic name past what it
   plainly means erodes the one thing a topic name is supposed to buy you.
3. **The four documented topics plus one addition for refunds, with
   authorize/capture folded into `payment-charged` and expiry folded into
   `payment-failed`.** Chosen. Authorization and capture confirmation are
   both part of the same "moving money towards being charged" phase that
   `payment-charged` already names; a decline during authorization
   (`PaymentAuthorized` with a failure reason) and `PaymentFailed` are
   deliberately kept on separate topics anyway, since one is a captured
   `PaymentFailed` event with its own topic. Expiry is a terminal outcome
   with no provider decline involved, but it is functionally the same
   "this authorization never became money" outcome that `payment-failed`
   already represents, and treating it as a fifth failure-adjacent topic
   would fragment failure handling for no consumer benefit. Refunds are the
   one genuinely distinct sub-lifecycle: they happen after a payment is
   already captured or completed, can recur multiple times against the same
   payment, and are a separate business process from the original charge —
   they earn their own topic rather than being shoehorned into an existing
   one.

## Consequences

- Five Kafka topics exist for the payment aggregate instead of four; the read
  projection (T-005) and any other future consumer need to know about
  `payment-refunded` in addition to the ones `TECH_STACK.md` already named.
- Consumers of `payment-charged` and `payment-failed` must switch on the
  `eventType` field rather than assuming one event shape per topic. This is a
  one-time integration cost paid once by each consumer, not a recurring one.
- Adding a genuinely new payment lifecycle concern in the future (e.g. a
  dispute/chargeback flow) should default to this same question — does it
  share an existing topic's meaning, or does it need its own — rather than
  either extreme.
- `governance/TECH_STACK.md` should be updated to list all five topics; that
  edit is out of this ADR's scope but is flagged in the T-007 handoff log.

## References

- `governance/ARCHITECTURE_RULES.md` section 7 (event-driven systems).
- `tasks/T-007-payment-kafka-publisher.md` — the task this decision unblocked.
- `src/main/java/com/orvigas/payment/publish/PaymentKafkaTopics.java` — the
  constants implementing this mapping.
