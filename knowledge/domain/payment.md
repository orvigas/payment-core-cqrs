# Payment

The Payment is the central aggregate root of the platform. It represents a single attempt to move money from a customer to a [[merchant]] and owns the full lifecycle of that attempt: initiation, authorization, capture, completion, failure, and refund accounting. Every other domain object in this folder exists relative to a Payment.

In CQRS terms, the Payment aggregate lives on the command side (Axon, MongoDB event store). Its state is never stored directly; it is rebuilt by replaying its events. Read models in Postgres are projections of those events and must never be treated as the source of truth for a business decision.

## Aggregate boundary

Captures and refunds are modeled as entities inside the Payment aggregate, not as separate aggregates. The invariants that matter (captured total never exceeds authorized amount, refunded total never exceeds captured total) span the payment and its captures/refunds, and an aggregate is exactly the consistency boundary that can enforce such rules transactionally. See [[capture]] and [[refund]] for the entity details. [[settlement]] is a separate aggregate because it belongs to the merchant's payout cycle, not to a single payment.

## Properties

| Property | Type | Meaning |
| --- | --- | --- |
| `paymentId` | UUID | Aggregate identifier. Application-assigned, so new-vs-existing must be modeled explicitly (see ARCHITECTURE_RULES on the null-ID heuristic). |
| `merchantId` | UUID | The [[merchant]] receiving the funds. Immutable after initiation. |
| `customerId` | UUID | The paying customer. Used for authorization checks and velocity/risk rules. |
| `amount` | Money | Requested amount. Money is a value object: `long` minor units plus an ISO 4217 currency code. Never a floating-point type. |
| `authorizedAmount` | Money | Amount the issuer approved. Usually equals `amount`; can differ with partial authorizations. |
| `capturedAmount` | Money | Running sum of succeeded [[capture]] entities. Derived from events, kept on the aggregate so the invariant check is O(1). |
| `refundedAmount` | Money | Running sum of succeeded [[refund]] entities. Same rationale. |
| `status` | enum | `INITIATED`, `AUTHORIZED`, `PARTIALLY_CAPTURED`, `CAPTURED`, `COMPLETED`, `FAILED`, `EXPIRED`, `PARTIALLY_REFUNDED`, `REFUNDED`. Modeled as a sealed hierarchy or enum; transitions are validated in command handlers, never set freely. |
| `paymentMethod` | value object | Tokenized instrument reference (card token, wallet reference). Raw PAN or credentials never enter the domain model; only provider tokens do. |
| `idempotencyKey` | String | Client-supplied key that makes initiation idempotent. A retry with the same key must return the original payment instead of creating a second one. |
| `authorizationCode` | String | Issuer/provider reference returned on authorization. Needed for capture, refund, and dispute calls to the provider. |
| `failureReason` | value object | Machine-readable code plus human-readable message when the payment fails. Codes distinguish retryable failures (network, provider timeout) from terminal ones (declined, fraud). |
| `authorizationExpiresAt` | Instant | Deadline after which the authorization can no longer be captured (card schemes typically allow about 7 days). Drives the `EXPIRED` transition. |
| `createdAt` / `updatedAt` | Instant | Audit timestamps. `updatedAt` is derived from the last applied event. |

## Behavior (command handlers)

| Method | Preconditions | Emits |
| --- | --- | --- |
| `initiate(...)` | Merchant is `ACTIVE`, amount is positive, currency supported, idempotency key unused | `PaymentInitiated` |
| `authorize(providerResult)` | Status is `INITIATED` | `PaymentAuthorized` or `PaymentFailed` |
| `capture(amount)` | Status is `AUTHORIZED` or `PARTIALLY_CAPTURED`; `capturedAmount + amount <= authorizedAmount`; authorization not expired | `PaymentCharged` (topic `payment-charged`) |
| `complete()` | All intended captures succeeded | `PaymentCompleted` |
| `fail(reason)` | Any non-terminal status | `PaymentFailed` |
| `expire()` | `AUTHORIZED` and past `authorizationExpiresAt`; triggered by a scheduled deadline, not a poller | `PaymentExpired` |
| `refund(amount, reason)` | Status includes captured funds; `refundedAmount + amount <= capturedAmount` | `RefundRequested` (see [[refund]]) |

Command handlers validate and decide; event-sourcing handlers only mutate state. No side effects in event handlers on the aggregate.

## Events

`PaymentInitiated`, `PaymentAuthorized`, `PaymentCharged`, `PaymentCompleted`, `PaymentFailed`, `PaymentExpired`. These map to the Kafka topics `payment-initiated`, `payment-charged`, `payment-completed`, `payment-failed`. Events are immutable, versioned, typed records; consumers must be idempotent because Kafka delivery is at-least-once.

## Invariants

- `capturedAmount <= authorizedAmount` at all times.
- `refundedAmount <= capturedAmount` at all times.
- Currency is fixed at initiation; every capture and refund must match it.
- Terminal states (`COMPLETED`, `FAILED`, `EXPIRED`, `REFUNDED`) accept no further money-moving commands. Refunds against a `COMPLETED` payment are the one deliberate exception, since refunds arrive after completion by nature.

## Design notes

- Amounts use minor units to avoid rounding drift; formatting to major units is a presentation concern.
- The status enum intentionally distinguishes `CAPTURED` (funds claimed from the issuer) from `COMPLETED` (payment finished from the platform's perspective, eligible for [[settlement]]). Collapsing them loses the window in which capture succeeded but downstream bookkeeping has not.
- Long-running flows that span providers (authorize at one, capture later, compensate on failure) are coordinated by a saga, not by the aggregate itself.
