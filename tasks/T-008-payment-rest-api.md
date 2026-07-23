---
id: T-008
title: Payment REST API
status: backlog
owner: none
branch: none
depends-on: [T-003, T-004]
---

# T-008: Payment REST API

## Goal

The `Payment` aggregate becomes reachable over HTTP: a merchant or client can initiate a payment, capture funds, and request a refund through a documented, authenticated, reactive REST surface. Today the aggregate only exists behind command-handler tests — there is no external entry point at all.

## Scope

- `src/main/java/com/orvigas/payment/api/`
- `src/test/java/com/orvigas/payment/api/`

## Acceptance criteria

- [ ] `POST /payments` initiates a payment: accepts merchant id, customer id, amount/currency, payment method token, and client-supplied idempotency key; a retry with the same key returns the original payment rather than creating a second one (`knowledge/domain/payment.md`)
- [ ] `POST /payments/{paymentId}/captures` and `POST /payments/{paymentId}/refunds` expose the corresponding aggregate commands, rejecting requests that violate the amount invariants (`capturedAmount <= authorizedAmount`, `refundedAmount <= capturedAmount`) with a 4xx, not a 500
- [ ] Every error path returns RFC7807 Problem Details (reuse the existing `ProblemDetail`/`GlobalErrorHandler` machinery from the security chain rather than inventing a second error format)
- [ ] Request bodies validated with Jakarta Bean Validation; invalid input never reaches the aggregate
- [ ] Endpoints sit behind the existing reactive JWT security chain (`SecurityConfig`); document which role(s) each endpoint requires
- [ ] Every endpoint carries OpenAPI annotations and is visible at `/swagger-ui.html`
- [ ] No blocking calls anywhere on the request path
- [ ] Integration tests (`WebTestClient`) cover the happy path and each rejection path (invalid amount, unknown payment id, replayed idempotency key, invariant violation)
- [ ] `mvn verify` passes

## Notes

`authorize`, `complete`, `fail`, and `expire` are provider-callback or system-triggered transitions per `knowledge/domain/payment.md`, not actions a client calls directly — this task deliberately covers only the client-facing commands (`initiate`, `capture`, `refund`). If a provider webhook endpoint turns out to be needed sooner, that's a separate task, not scope creep here.

The merchant-`ACTIVE` precondition on `initiate` is not enforced yet (no `Merchant` aggregate exists — see T-009/T-010); this task can either stub that check or land ahead of T-010 and wire it in later. Don't block on it.

## Handoff log
