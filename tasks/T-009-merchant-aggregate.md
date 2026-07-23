---
id: T-009
title: Merchant aggregate with onboarding and lifecycle commands
status: backlog
owner: none
branch: none
depends-on: [T-001, T-002]
---

# T-009: Merchant aggregate with onboarding and lifecycle commands

## Goal

A `Merchant` aggregate exists on the command side, modeling onboarding, KYB verification, activation, suspension, and closure exactly as specified in `knowledge/domain/merchant.md`. This closes the correctness gap where nothing in the codebase actually enforces "only an `ACTIVE` merchant can initiate a payment."

## Scope

- `src/main/java/com/orvigas/merchant/`
- `src/test/java/com/orvigas/merchant/`

## Acceptance criteria

- [ ] Commands and events cover the full lifecycle in `knowledge/domain/merchant.md`: `register`, `completeKyb`, `activate`, `suspend`, `reinstate`, `updateSettlementAccount`, `updateFeeSchedule`, `close`
- [ ] `activate()` only succeeds with verified KYB and a verified settlement account — both preconditions enforced, not just documented
- [ ] `suspend()` blocks new payments conceptually but the aggregate itself doesn't reach into payments; it just records the status transition other services read
- [ ] Fee schedule changes are forward-only (`effectiveFrom` must be in the future); a settled historical schedule is never mutated
- [ ] `close()` is rejected when the merchant has open settlements or a held reserve
- [ ] Bank account details and tax ids are treated as classified data per `governance/SECURITY_POLICY.md` — encrypted or stored as references in events, never in plaintext, and excluded from log output
- [ ] `MerchantId` (already defined in `shared/id`) is used as the aggregate identifier; application-assigned id handled with explicit new-vs-existing logic, not a null-id heuristic
- [ ] Tests cover every valid transition and reject every invalid one (e.g., `activate()` before KYB verification, `close()` with an open settlement)
- [ ] `mvn verify` passes

## Notes

This is the write-side aggregate only. The lightweight status projection that the payment flow reads (`knowledge/domain/merchant.md`, "Design notes") is T-010, and depends on this task's events existing first.

## Handoff log
