# Refund

A Refund returns previously captured money to the customer. It is not a rollback: the original [[payment]] happened, was settled or will settle, and the refund is a second, opposite movement of funds with its own lifecycle, its own provider call, and its own line in [[settlement]]. Modeling it as "undo" is a classic mistake; the books must show both movements.

Refunds are entities inside the payment aggregate, for the same reason as [[capture]]: the invariant "total refunded never exceeds total captured" spans the payment and all its refunds, so a single consistency boundary must own it.

## Refund vs void/reversal

If the payment is authorized but not yet captured, the correct operation is a void (authorization reversal): the hold is released and no money ever moved. A refund applies only to captured funds. The API should not let callers refund an uncaptured payment; it should direct them to cancel instead. Conflating the two produces support tickets ("where is my refund?") for money that was never taken.

## Properties

| Property | Type | Meaning |
| --- | --- | --- |
| `refundId` | UUID | Entity identifier. Referenced by settlement entries and provider reconciliation. |
| `paymentId` | UUID | Owning [[payment]]. |
| `captureId` | UUID | The [[capture]] being refunded, when the provider requires refunds to target a specific capture. Nullable for providers that refund at payment level. |
| `amount` | Money | Refund amount in the payment currency. Positive; bounded by captured-minus-already-refunded. |
| `reason` | enum + text | Structured reason (`REQUESTED_BY_CUSTOMER`, `DUPLICATE`, `FRAUD`, `ORDER_CANCELLED`) plus optional free text. Structured codes feed fraud analytics and merchant reporting; free text alone is useless for either. |
| `status` | enum | `REQUESTED`, `PENDING`, `SUCCEEDED`, `FAILED`. `REQUESTED` is recorded before the provider call; `PENDING` while the provider processes it (card refunds can take days to land). |
| `idempotencyKey` | String | Makes refund requests safe to retry. A double-clicked refund button must not return money twice. |
| `initiatedBy` | value object | Who triggered it: merchant user, platform operator, or an automated rule (for example a dispute outcome). Required for the audit trail. |
| `providerReference` | String | Provider identifier for the refund transaction. |
| `failureReason` | value object | Provider code and message when the refund fails (expired card, closed account). Failed refunds usually need a fallback payout channel and are an operational, not purely technical, concern. |
| `requestedAt` / `resolvedAt` | Instant | Lifecycle timestamps. |

## Behavior

| Method | Effect |
| --- | --- |
| `Payment.refund(amount, reason, idempotencyKey)` | Validates invariants, emits `RefundRequested`, records the entity as `REQUESTED`. |
| `Payment.confirmRefund(refundId, providerReference)` | Provider accepted. Status moves through `PENDING` to `SUCCEEDED`; `refundedAmount` increases; payment becomes `PARTIALLY_REFUNDED` or `REFUNDED`. |
| `Payment.failRefund(refundId, reason)` | Provider rejected. Status `FAILED`; the amount becomes refundable again. |

Events: `RefundRequested`, `RefundSucceeded`, `RefundFailed`. As everywhere, consumers must be idempotent and the events are immutable versioned records.

## Invariants

- `sum(REQUESTED + PENDING + SUCCEEDED refunds) <= capturedAmount`. In-flight refunds count against the limit so concurrent requests cannot over-refund.
- Refunds require captured funds; an uncaptured payment is voided, never refunded.
- Partial refunds are allowed, and multiple partial refunds may target the same capture as long as the invariant holds.
- Currency always matches the payment currency.
- A refund, once `SUCCEEDED`, is itself immutable. Refunding a refund does not exist; a correction is a new payment.

## Settlement impact

A succeeded refund produces a debit line in the merchant's [[settlement]] batch. If refunds outpace new captures, a settlement period can net negative, which is why [[merchant]] carries reserve and payout configuration.
