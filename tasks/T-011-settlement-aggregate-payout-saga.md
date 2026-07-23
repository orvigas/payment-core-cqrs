---
id: T-011
title: Settlement aggregate and payout saga
status: backlog
owner: none
branch: none
depends-on: [T-007, T-009]
---

# T-011: Settlement aggregate and payout saga

## Goal

A `Settlement` aggregate batches captured payments and refunds into a periodic merchant payout, and a Saga drives the payout through to completion or failure — the platform's first Saga, and the reference implementation for the Vision's "long-running flows are Sagas, not distributed transactions" principle.

## Scope

- `src/main/java/com/orvigas/settlement/`
- `src/main/resources/db/migration/`
- `src/test/java/com/orvigas/settlement/`

## Acceptance criteria

- [ ] `Settlement` aggregate keyed by `(merchantId, period, currency)`, one per combination, implementing `open`, `addEntry`, `close`, `initiatePayout`, `confirmPayout`, `failPayout` per `knowledge/domain/settlement.md`
- [ ] `addEntry` deduplicates by entry reference id (`captureId`/`refundId`) — this is the idempotency mechanism that makes the projection safe against Kafka redelivery, not a separate dedup layer
- [ ] `netAmount` is always the derived sum of entries; never adjusted independently of them
- [ ] A `CLOSED` settlement is immutable except for payout status; corrections land as entries in a later settlement, never as edits to a closed one (mirrors the append-only migration rule)
- [ ] A consumer populates settlement entries from the `payment-charged` and refund topics, resolving the merchant's open settlement for the event's period; idempotent and tolerant of out-of-order delivery
- [ ] A Saga coordinates `SettlementPayoutInitiated` through to `SettlementPaidOut`/`SettlementPayoutFailed`, with a failed payout retried against corrected bank details rather than the settlement being recomputed
- [ ] The actual bank transfer call is behind a port/interface — no real payment-provider integration exists yet, and this task should not invent one; the saga must be structured so a real integration can be substituted later without a redesign
- [ ] Read-side Postgres projection for settlement reports: totals per period, entry drill-down, search by capture reference
- [ ] `mvn verify` passes

## Notes

This is the largest and most speculative task in the current backlog — it introduces a new pattern (Saga) with no prior example in the codebase. Per `workflow/DEVELOPMENT_WORKFLOW.md`, consider routing the saga's compensation/retry design through the `architect` agent before implementation starts, even though the domain shape is already documented in `knowledge/domain/settlement.md`.

## Handoff log
