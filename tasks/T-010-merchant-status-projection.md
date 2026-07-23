---
id: T-010
title: Merchant status projection and payment-side enforcement
status: backlog
owner: none
branch: none
depends-on: [T-009, T-007]
---

# T-010: Merchant status projection and payment-side enforcement

## Goal

The payment initiation path actually checks merchant status against a real projection instead of trusting the caller — closing the loop the `Merchant` aggregate (T-009) opened. Per `knowledge/domain/merchant.md`, this is a dedicated lightweight projection (status, supported currencies), separate from the full merchant profile view, because the payment path needs it at high read volume.

## Scope

- `src/main/java/com/orvigas/merchant/publish/`
- `src/main/java/com/orvigas/merchant/projection/`
- `src/main/resources/db/migration/`
- `src/main/java/com/orvigas/payment/PaymentCommandHandler.java` (adding the merchant-status check to `initiate`)
- `src/test/java/com/orvigas/merchant/`

## Acceptance criteria

- [ ] Merchant events relevant to the payment path (`MerchantActivated`, `MerchantSuspended`, `MerchantReinstated`, `MerchantClosed`, currency-support changes) are published to Kafka after durable commit, mirroring the pattern established in T-007
- [ ] Projection table holds only `merchantId`, `status`, and `supportedCurrencies` — not the full merchant profile
- [ ] Consumer is idempotent under duplicate and out-of-order delivery
- [ ] Portable column types with explicit constraints; no native Postgres enums
- [ ] `Payment.initiate()` reads this projection and rejects initiation for a non-`ACTIVE` merchant or an unsupported currency, accepting the documented small staleness window (a merchant suspended mid-flight is caught by the next command, not this one)
- [ ] Testcontainers integration test covers event-to-row flow end to end, plus a payment-initiation test against a suspended/unknown merchant
- [ ] `mvn verify` passes

## Notes

This task modifies `PaymentCommandHandler.java`, which is also touched by T-008 (payment REST API) only indirectly (T-008 calls the handler, doesn't change its preconditions) — check the ledger before starting in case T-008 is in progress and touching the same handler for unrelated reasons, and coordinate rather than let both land conflicting changes.

## Handoff log
