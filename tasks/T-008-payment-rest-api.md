---
id: T-008
title: Payment REST API
status: review
owner: backend-engineer
branch: task/T-008-payment-rest-api
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

### 2026-07-22 — backend-engineer

**What was done**
- Created `PaymentController` with 3 endpoints: POST `/payments`, POST `/{id}/captures`, POST `/{id}/refunds`
- Created `PaymentRestApiService` that translates REST DTOs to Axon commands via `CommandGateway.send()` (non-blocking, `Mono.fromFuture`)
- Request/response DTOs as Java records with Jakarta Bean Validation and OpenAPI annotations
- Idempotency for initiate via `PaymentIdempotencyRepository` (reused from T-003); returns 200 on duplicate key, 201 on new
- Idempotency for refunds handled by aggregate (pass-through)
- Error handling maps `IllegalStateException`/`IllegalArgumentException` (direct or wrapped in `CommandExecutionException`) → 400 RFC 7807, `AggregateNotFoundException` → 404, validation errors → 400
- Integration test (`PaymentRestApiIntegrationTest`) with 13 scenarios covering happy path (initiate → authorize → capture → confirm → refund), duplicate keys, validation errors, unknown payments, invariant violations, and unauthenticated access

**Files outside scope that were modified**
- `CapturePaymentCommand.java` — added optional `CaptureId captureId` field (backward-compatible, existing 3-arg constructor preserved)
- `RefundPaymentCommand.java` — added optional `RefundId refundId` field (backward-compatible, existing 6-arg constructor preserved)
- `Payment.java` — aggregate handlers use provided `captureId`/`refundId` from commands when non-null
- `GlobalErrorHandler.java` — added handlers for `IllegalStateException`, `IllegalArgumentException`, `CommandExecutionException`, `AggregateNotFoundException`
- `Money.java` — added `@JsonIgnore` on `isZero()`/`isPositive()` and `@JsonIgnoreProperties(ignoreUnknown = true)` to prevent Jackson serialization conflicts with Axon event store
- `test-harness-application.yml` — added `spring.data.mongodb.uuid-representation: standard`
- `MongoConfig.java` (new in `com.orvigas.config`) — `MongoClientSettingsBuilderCustomizer` setting `UuidRepresentation.STANDARD` for Axon event store compatibility

**Known issues**
- Refund duplicate idempotency returns a different `refundId` on the second response (aggregate handles idempotency internally but doesn't return the original ID; the REST layer generates a new one each time). The idempotency guarantee holds (no duplicate refund created), but the response body differs. A future task could add a refund idempotency store similar to payment initiation.
- No rate limiting wired to the payment endpoints yet (the `payment` rate limiter instance exists in config but isn't applied).

**Test results**
- `mvn verify`: 129 tests, 0 failures, 0 errors, coverage 95%+ met
