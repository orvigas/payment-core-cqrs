---
id: T-002
title: Shared Money value object and typed identifiers
status: backlog
owner: none
branch: none
depends-on: []
---

# T-002: Shared Money value object and typed identifiers

## Goal

The domain primitives every aggregate depends on: a `Money` value object (long minor units + ISO 4217 currency) and typed id wrappers (`PaymentId`, `MerchantId`, `CaptureId`, `RefundId`, `SettlementId`) under `shared/`.

## Scope

- `src/main/java/com/orvigas/shared/`
- `src/test/java/com/orvigas/shared/`

## Acceptance criteria

- [ ] `Money` is a record; arithmetic rejects currency mismatches and never uses floating point
- [ ] `Money` supports the operations the domain docs need: add, subtract, compare, isPositive, zero-of-currency
- [ ] Typed ids are records wrapping UUID with a `newId()` factory; no raw UUIDs cross aggregate boundaries
- [ ] Boundary cases tested: zero, negative rejection, overflow behavior on add, currency mismatch
- [ ] `mvn verify` passes

## Notes

Property semantics come from `knowledge/domain/payment.md` (Money definition) and apply identically across all five domain objects. Keep these free of Spring and Axon imports; they are plain domain types.

## Handoff log
