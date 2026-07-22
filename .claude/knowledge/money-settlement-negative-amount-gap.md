# Money's non-negative invariant doesn't fit Settlement's netAmount

- Date: 2026-07-22
- Affected versions/components: `com.orvigas.shared.money.Money` (T-002), future Settlement aggregate (not yet a ledger task)

## Problem

`Money` (`src/main/java/com/orvigas/shared/money/Money.java`) rejects negative amounts at construction — every arithmetic operation (`add`, `subtract`) enforces the result stays at or above zero. That's correct for `Payment`'s `capturedAmount`, `refundedAmount`, and `authorizedAmount`.

It does not fit `Settlement.netAmount`. `knowledge/domain/settlement.md` documents `netAmount` as `gross - refunds - fees` — "what actually gets transferred" — and states explicitly that it "can be negative when refunds dominate; a negative settlement carries over or draws on the merchant's reserve." `Money` as built cannot represent that value.

## Root cause

T-002 built `Money` as a single general-purpose value type and modeled the non-negative constraint as a property of `Money` itself, based on how `Payment`'s amounts behave. But non-negativity isn't actually a property of "an amount of money" in this domain — it's a property of specific fields on specific aggregates. `Settlement.netAmount` is a legitimate signed quantity; `Payment.capturedAmount` is not. One record type can't carry both constraints.

## Diagnosis

Caught in code review by cross-checking `Money`'s Javadoc claim ("mirrors how the domain model treats amounts") against `knowledge/domain/settlement.md` directly, rather than trusting that the implementer's stated domain-doc research covered every consumer. No Settlement task exists yet in `tasks/`, so nothing currently exercises this — it's a latent gap, not a live bug.

## Fix

Not yet decided — no code changes were made for this; T-002 shipped as-is since nothing in the current task ledger (T-001 through T-005) touches Settlement. Options for whoever scopes that work:

1. Add a signed variant (`SignedMoney` or similar) for fields that can legitimately go negative, keeping `Money` non-negative for everything else.
2. Drop the non-negative invariant from `Money` entirely and enforce non-negativity at the call site (aggregate command handlers) for the fields that need it.
3. Represent `netAmount` as a different type entirely (e.g., a `long` with explicit sign semantics documented on the field) rather than reusing `Money`.

Option 1 keeps the strongest compile-time guarantees for the common case (most amounts truly can't be negative) at the cost of a second type. Option 2 is simpler but pushes the invariant further from the type system. No decision has been made — this should become an ADR once a Settlement task is scoped, not decided in passing.

## Prevention

When a shared value type encodes an invariant, check that invariant against every documented consumer in `knowledge/domain/`, not just the aggregate that motivated the type's creation. `Money` was built primarily against `payment.md`; the gap only surfaced because review separately read `settlement.md`.

## Keywords

Money, negative amount, netAmount, settlement, signed money, value object invariant, shared domain type
