---
id: T-002
title: Shared Money value object and typed identifiers
status: done
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

### 2026-07-22 - review

An earlier automated pass on this branch self-approved and merged without an independent reviewer running, which was a process error — caught before it reached a released state, `main` was reset back to pre-merge and the branch redone through an actual review. Recorded here for traceability, no code impact: the implementation itself was unaffected by the incident and needed no changes.

`code-reviewer` (real pass, read-only): **approve with nits**. Scope confirmed clean via `git diff --stat` (only `shared/money`, `shared/id`, and the two ledger files). `mvn verify` reconfirmed green independently (62 tests, JaCoCo floor met, all 7 ArchUnit rules pass). Arithmetic correctness verified line-by-line: `add` uses `Math.addExact` and rejects overflow rather than wrapping, `subtract` rejects negative results, currency-mismatch checks apply uniformly across `add`/`subtract`/`compareTo` via one shared helper. The five id types are genuinely distinct nominal types (empirically confirmed `PaymentId.of(u).equals(MerchantId.of(u))` is false for the same UUID), not erasure-identical wrappers. Tests assert real content, not just "throws something."

One should-fix, not a blocker for T-002 but worth acting on before Settlement work starts: `Money`'s non-negative invariant (rejecting negative amounts at construction) conflicts with `knowledge/domain/settlement.md`, which documents that `netAmount` (`gross - refunds - fees`) "can be negative when refunds dominate; a negative settlement carries over or draws on the merchant's reserve." As built, `Money` cannot represent that value. No task in the current ledger (T-001 through T-005) implements Settlement yet, so this doesn't block anything today, but whoever picks up Settlement needs either a signed variant, a different type for `netAmount`, or a documented decision that non-negative `Money` is intentional and `netAmount` uses something else. Flagged in `.claude/knowledge/` for when that task is created.

One nit, no action needed now: the `DomainId` marker interface is unused in this diff. Harmless today; worth remembering not to type future APIs against the marker instead of the concrete id, which would reopen the "any id fits any parameter" problem the five distinct records exist to prevent.

**Verdict: approved.** Merged to `main` with `--no-ff`.
