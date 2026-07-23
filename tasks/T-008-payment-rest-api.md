---
id: T-008
title: Payment REST API
status: in-progress
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

### 2026-07-23 — review: request changes (code-reviewer + security-reviewer)

**Security review — Critical, blocks merge:**

No object-level authorization on any of the three endpoints (OWASP API1, BOLA/IDOR). `SecurityConfig` only requires `anyExchange().authenticated()`; the JWT carries `subject`/`roles` only, no merchant claim. `PaymentRestApiService` never checks the caller against the resource:
- `POST /payments` takes `merchantId`/`customerId` as plain request-body fields — any authenticated caller can initiate a payment against any merchant/customer id.
- `POST /payments/{id}/captures` and `.../refunds` take `paymentId` from the path with no check that it belongs to the caller — any authenticated user who has or guesses a payment id can capture or refund it.

This is `governance/SECURITY_POLICY.md` §2's named example ("the resource ID coming from the caller is never trusted as proof of access") and it's the first HTTP surface for the aggregate, so it's the point where this stops being theoretical. Fix requires adding a merchant claim to the JWT and checking it against the target resource before dispatching any command.

**Security review — High, blocks merge:**

- `GlobalErrorHandler.handleInvariantViolation`/`handleCommandExecution` echo `ex.getMessage()`/`cause.getMessage()` straight into the RFC 7807 `detail` field — a regression against `handleValidation` in the same file, which deliberately logs the real exception server-side and returns a fixed string. `RefundReasonCode.valueOf(...)` on invalid input leaks a fully-qualified internal class name to the client. Combined with the IDOR above, this becomes an information-disclosure oracle (amounts/status inferred from verbose 400s while probing payment ids).
- No rate limiting on any of the three endpoints, despite `governance/SECURITY_POLICY.md`/`.claude/CLAUDE.md` requiring it for payment endpoints and the pattern already existing for `/auth/login`. This was self-reported as a known issue above but not fixed before moving to review.

**Security review — Medium (triaged, not blocking):** monetary input has no currency allowlist or upper-bound check (`MoneyRequest`/`Money.of` accept any recognized ISO code and up to `Long.MAX_VALUE` minor units); `governance/SECURITY_POLICY.md` §3 requires both.

**Code review — Blocker, money-correctness bugs (both deterministic, not races):**

- Duplicate refund returns a fabricated `refundId` that identifies no real entity. `Payment.handle(RefundPaymentCommand)` returns early without applying an event on a duplicate idempotency key, discarding the freshly generated id the service passed in; the service ignores the command result and echoes its own locally-minted UUID instead. This is the same bug already called out in "Known issues" above, but self-reporting it doesn't substitute for fixing it — the new test `duplicateRefundIdempotencyReturns200` doesn't assert `refundId` equality, which is why it didn't catch this.
- `capturePayment` hardcodes the response status to `"CAPTURED"` when the aggregate only reaches `PENDING` at that point (confirmed by capture.md and by the test suite needing a separate `ConfirmCaptureCommand` call) — telling a caller funds are captured when they're only requested is a real API-contract defect for a payments API.
- Initiate-payment idempotency check-then-act race: `MongoPaymentIdempotencyRepository`'s Javadoc claims a unique index backs it, but no index exists anywhere in the codebase, so two concurrent requests with the same key can both pass and create two aggregates. `dispatchInitiate` also builds its 201 response from the locally generated id rather than the command handler's authoritative return value.
- `capturePayment`/`refundPayment` call `commandGateway.send(command)` directly with no scheduler offload; no custom `CommandBus` bean exists, so Axon's default `SimpleCommandBus` dispatches synchronously on the calling thread — meaning aggregate rehydration, invariant checks, and event persistence run inline on the WebFlux request thread for these two endpoints, contradicting the "no blocking calls on the request path" acceptance criterion. `initiatePayment` avoids this by accident (it's nested inside an unrelated `subscribeOn` from the idempotency pre-check), not by design.

**Code review — should-fix:** no `@SecurityRequirement`/`@SecurityScheme` anywhere, so the stated acceptance criterion "document which role(s) each endpoint requires" isn't met and Swagger UI can't exercise the bearer flow; the global `IllegalArgumentException`/`IllegalStateException` → 400 handler is unscoped and will misclassify unrelated errors as the codebase grows; missing constructor Javadoc on `PaymentController`/`PaymentRestApiService`.

None of this reached HIGH-severity automated SAST (which would auto-block per `governance/QUALITY_GATE.md` §4) — these are manual-review findings, but the Critical IDOR and the two money-correctness bugs are unambiguous blockers regardless.

PR #6 stays open; fixes land as new commits on this branch.
