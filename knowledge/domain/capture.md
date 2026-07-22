# Capture

A Capture is the act of actually claiming money that a previous authorization reserved. Card networks separate the two steps on purpose: authorization checks funds and places a hold, capture moves them. The gap between the steps is what lets a merchant authorize at checkout and capture only when goods ship, adjust the amount downward, or release the hold entirely.

Captures are entities inside the [[payment]] aggregate, identified by their own `captureId` but only reachable through the owning payment. They are not aggregates themselves: the rule "sum of captures never exceeds the authorized amount" must be enforced atomically, and only the aggregate can do that.

## Properties

| Property | Type | Meaning |
| --- | --- | --- |
| `captureId` | UUID | Entity identifier. Unique across the platform, not just within the payment, because [[settlement]] entries and provider reconciliation reference it directly. |
| `paymentId` | UUID | Owning [[payment]]. |
| `amount` | Money | Amount claimed in this capture, in the payment's currency. Positive, and bounded by the remaining authorized amount at the time the command is handled. |
| `status` | enum | `PENDING`, `SUCCEEDED`, `FAILED`. `PENDING` exists because the provider call is asynchronous; the capture is recorded before the provider confirms. |
| `providerReference` | String | Identifier the payment provider returns for this capture. Required for reconciliation and for refunding against a specific capture. |
| `isFinal` | boolean | Marks this as the last capture for the payment. A final capture releases any remaining authorization hold, which matters to the customer (the hold disappears from their account) and to scheme fees. |
| `failureReason` | value object | Provider decline code and message when the capture fails. |
| `requestedAt` / `resolvedAt` | Instant | When the capture was requested and when the provider confirmed or declined it. The gap is a useful operational metric. |

## Behavior

Captures are created and resolved through commands on the payment aggregate; there is no standalone capture command handler.

| Method | Effect |
| --- | --- |
| `Payment.capture(amount, isFinal)` | Validates the invariant, emits `PaymentCharged` with a new `captureId`, records the entity as `PENDING`. |
| `Payment.confirmCapture(captureId, providerReference)` | Provider confirmed. Capture becomes `SUCCEEDED`, `capturedAmount` increases, payment moves to `PARTIALLY_CAPTURED` or `CAPTURED`. |
| `Payment.failCapture(captureId, reason)` | Provider declined. Capture becomes `FAILED`; the reserved amount returns to the available-to-capture pool. |

## Full, partial, and multiple captures

- Full capture: one capture for the entire authorized amount. The common case; most flows should default to it with `isFinal = true`.
- Partial capture: less than the authorized amount, typical when part of an order is out of stock. The remainder of the hold is released if the capture is final.
- Multiple captures: several partial captures against one authorization, used for split shipments. Each needs provider support; not every acquirer allows it, so the merchant configuration decides whether the platform accepts more than one capture per payment.

## Invariants

- Sum of `PENDING` and `SUCCEEDED` capture amounts never exceeds `authorizedAmount`. Pending amounts count against the limit, otherwise concurrent captures could overshoot.
- No capture after the authorization expires (`authorizationExpiresAt` on the payment). Late captures are declined by the scheme anyway, but the domain should refuse them first.
- No capture after a final capture has succeeded.
- Capture currency always equals the payment currency.

## Relationship to settlement

A succeeded capture is the unit that eventually appears as a credit line in a [[settlement]] batch for the [[merchant]]. Settlement references `captureId`, so captures must be individually identifiable long after the payment reaches a terminal state. This is why capture events carry the full amount and identifiers rather than relying on a lookup against mutable state.
