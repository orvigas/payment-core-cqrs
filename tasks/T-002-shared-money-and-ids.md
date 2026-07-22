---
id: T-002
title: Shared Money value object and typed identifiers
status: review
owner: backend-engineer
branch: task/T-002-shared-money-and-ids
depends-on: []
---

# T-002: Shared Money value object and typed identifiers

## Goal

The domain primitives every aggregate depends on: a `Money` value object (long minor units + ISO 4217 currency) and typed id wrappers (`PaymentId`, `MerchantId`, `CaptureId`, `RefundId`, `SettlementId`) under `shared/`.

## Scope

- `src/main/java/com/orvigas/shared/`
- `src/test/java/com/orvigas/shared/`

## Acceptance criteria

- [x] `Money` is a record; arithmetic rejects currency mismatches and never uses floating point
- [x] `Money` supports the operations the domain docs need: add, subtract, compare, isPositive, zero-of-currency
- [x] Typed ids are records wrapping UUID with a `newId()` factory; no raw UUIDs cross aggregate boundaries
- [x] Boundary cases tested: zero, negative rejection, overflow behavior on add, currency mismatch
- [x] `mvn verify` passes

## Notes

Property semantics come from `knowledge/domain/payment.md` (Money definition) and apply identically across all five domain objects. Keep these free of Spring and Axon imports; they are plain domain types.

## Handoff log

- 2026-07-22: Implemented `Money` (`shared/money`) as a record over `long` minor units and `java.util.Currency`, with `add`, `subtract`, `compareTo` (implements `Comparable<Money>`), `isPositive`, `isZero`, `zero(Currency)`, and `of(long, String)`. Added a small sealed exception hierarchy (`MoneyException` permits `CurrencyMismatchException`, `InvalidMoneyAmountException`) so currency mismatches, negative amounts, and `add` overflow (via `Math.addExact`, wrapped rather than leaking `ArithmeticException`) are typed, catchable failures instead of bare runtime exceptions. `subtract` rejects results below zero for the same reason — amounts never go negative.
  Added the five typed id records under `shared/id` (`PaymentId`, `MerchantId`, `CaptureId`, `RefundId`, `SettlementId`), each wrapping `UUID`, implementing a shared `DomainId` marker interface, with `newId()`, `of(UUID)`, and `fromString(String)` factories and a null-check compact constructor. Semantics for all five came from `knowledge/domain/payment.md`, `merchant.md`, `capture.md`, `refund.md`, and `settlement.md`.
  Full unit coverage under `src/test/java/com/orvigas/shared/` (StepVerifier not applicable — no reactive types here, plain JUnit 5 + AssertJ per the coding standard). Boundary cases covered: zero amount, negative rejection at construction and on subtract, `Long.MAX_VALUE` overflow on add, cross-currency add/subtract/compareTo, malformed UUID strings, null rejection, id uniqueness and equality.
  `mvn verify` is green: all 7 `ArchitectureRulesTest` rules pass unchanged (no Spring/Axon/JPA imports in scope), JaCoCo instruction coverage for `shared/money` and `shared/id` is 98-99%, overall project coverage 98%, comfortably above the 95% floor.
  Only file touched outside the declared scope is `tasks/TASKS.md`, updated to mirror this task's status per the ledger-sync convention in `.claude/rules/project-knowledge.md`; no other files outside `src/main/java/com/orvigas/shared/` and `src/test/java/com/orvigas/shared/` were changed.
