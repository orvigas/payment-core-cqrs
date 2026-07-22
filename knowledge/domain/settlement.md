# Settlement

A Settlement is the periodic batch that turns captured customer money into an actual payout to a [[merchant]]. Individual payments move funds from customers to the platform's acquiring account; settlement aggregates those movements over a period (typically daily), nets out refunds and fees, and produces one bank transfer to the merchant. Merchants reconcile their bank statement against settlement reports, so correctness and traceability here rank above everything else.

Settlement is its own aggregate, keyed by merchant and period, not part of the [[payment]] aggregate. A settlement spans thousands of payments and belongs to the merchant's payout cycle; pulling it into the payment aggregate would make that aggregate unbounded.

## Properties

| Property | Type | Meaning |
| --- | --- | --- |
| `settlementId` | UUID | Aggregate identifier. Appears on merchant reports and bank transfer references. |
| `merchantId` | UUID | The [[merchant]] being paid out. |
| `period` | date range | The settlement window (usually one calendar day in the merchant's settlement timezone). Boundary handling matters: a capture at 23:59 must land in exactly one period. |
| `currency` | ISO 4217 | Settlement currency. One settlement per currency; a merchant taking EUR and USD gets two batches per period. |
| `grossAmount` | Money | Sum of captured amounts in the period. |
| `refundAmount` | Money | Sum of refunds executed in the period. Note these are refunds by execution date, not refunds of this period's payments. |
| `feeAmount` | Money | Platform and scheme fees, computed from the merchant's fee schedule per line item and summed. Itemized, never a single opaque number. |
| `netAmount` | Money | `gross - refunds - fees`. What actually gets transferred. Can be negative when refunds dominate; a negative settlement carries over or draws on the merchant's reserve. |
| `entries` | list | Settlement lines, one per money movement: `CAPTURE` (credit, references `captureId`), `REFUND` (debit, references `refundId`), `FEE`, `ADJUSTMENT`, `CHARGEBACK`. Each entry keeps amount, type, reference id, and timestamp. Entries are the audit trail; totals are derived from them. |
| `status` | enum | `OPEN` (accumulating entries), `CLOSED` (period ended, totals frozen), `PAID_OUT` (transfer executed), `FAILED` (transfer bounced). |
| `payoutReference` | String | Bank transfer reference once the payout executes. |
| `closedAt` / `paidOutAt` | Instant | Lifecycle timestamps. |

## Behavior

| Method | Preconditions | Effect |
| --- | --- | --- |
| `open(merchantId, period, currency)` | No existing settlement for the same merchant, period, and currency | Emits `SettlementOpened`. |
| `addEntry(entry)` | Status `OPEN`; entry reference not already present | Emits `SettlementEntryAdded`. Deduplication by reference id is what makes the projection safe against Kafka redelivery. |
| `close()` | Status `OPEN`; period elapsed | Freezes totals, emits `SettlementClosed`. After this, no entries; late-arriving items go to the next period as adjustments. |
| `initiatePayout()` | Status `CLOSED`; net positive; merchant payout details valid | Emits `SettlementPayoutInitiated`; a saga drives the bank transfer. |
| `confirmPayout(reference)` / `failPayout(reason)` | Payout in flight | Emits `SettlementPaidOut` or `SettlementPayoutFailed`. A failed payout is retried against corrected bank details; the settlement itself is never recomputed. |

## How entries arrive

Settlement is populated by consuming the payment event stream (`payment-charged`, refund events) from Kafka. The consumer resolves the merchant's open settlement for the event's period and issues `addEntry`. Because delivery is at-least-once, idempotency lives in `addEntry` via the entry reference id, not in the consumer.

## Invariants

- Exactly one settlement per (merchant, period, currency).
- `netAmount` always equals the sum of entries. Totals are never adjusted independently of entries.
- A `CLOSED` settlement is immutable except for payout status. Corrections are adjustment entries in a later settlement, mirroring how migrations are append-only: the historical record is never edited.
- Every entry traces back to a domain object (`captureId`, `refundId`) or an explicit adjustment with an operator and reason.

## Read side

Merchant-facing settlement reports are Postgres projections of settlement events, optimized for the queries merchants actually run: totals per period, entry drill-down, and search by capture reference.
