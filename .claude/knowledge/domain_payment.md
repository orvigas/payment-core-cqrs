---
name: domain_payment
description: Payment aggregate root — core domain model for payment processing lifecycle
metadata:
  type: knowledge
---

# Payment Aggregate

## Overview

A `Payment` is the core aggregate root representing a financial transaction initiated by a customer at a merchant's request. It models the complete lifecycle: creation → authorization → capture → settlement → optional refund. Payments are idempotent and immutable after commitment.

## Aggregate Root: Payment

### Identity

- **PaymentId**: Unique identifier, UUID, assigned at creation. Never reused. Used in all payment-related events.
- **IdempotencyKey**: Client-supplied or server-generated unique key. Prevents duplicate charges on retry. Indexed; queries within 24 hours return the same payment.

### Core Properties

```java
Payment {
  // Identity
  PaymentId id;
  String idempotencyKey;
  
  // Participants
  MerchantId merchantId;
  CustomerId customerId;
  String customerEmail;
  
  // Amount & Currency
  Money amount;                    // Decimal + currency code (ISO 4217)
  Money amountCaptured = ZERO;     // Incremental; supports partial captures
  Money amountRefunded = ZERO;     // Incremental; supports partial refunds
  
  // Payment method (tokenized, never raw PAN)
  PaymentMethodToken paymentMethodToken;
  PaymentMethodType type;          // CARD, BANK_TRANSFER, DIGITAL_WALLET
  String maskedPan;                // e.g., "****1234" (display only)
  
  // State & Status
  PaymentStatus status;            // CREATED → AUTHORIZED → CAPTURED → SETTLED
  PaymentFailureReason failureReason;
  
  // Reference & Metadata
  String orderReference;           // Merchant's order ID
  String description;              // Human-readable payment purpose
  Map<String, String> metadata;    // Custom merchant data (JSON)
  
  // Timestamps
  Instant createdAt;
  Instant authorizedAt;
  Instant capturedAt;
  Instant settledAt;
  Instant failedAt;
  
  // Audit & Compliance
  String providerTransactionId;    // Gateway reference for dispute resolution
  String authorizationCode;        // For card auth success
  int retryCount;                  // Tracks retries on transient failures
}
```

### Value Objects

**Money**:
- `BigDecimal amount` + `Currency code` (e.g., "USD", "EUR", "GBP")
- Always represents the exact amount, never fractional cents
- Includes validation: amount > 0, currency is valid ISO 4217

**PaymentStatus** (enum):
- `CREATED` — Payment record exists; authorization not yet attempted
- `AUTHORIZED` — Authorization succeeded; funds reserved (if card auth) or accepted (bank transfer). Capture pending within 7 days (varies by scheme)
- `CAPTURED` — Amount debited from customer; ready for settlement
- `SETTLED` — Funds deposited to merchant account (via settlement batch)
- `FAILED` — Terminal state; authorization declined or captured amount reversed
- `REFUNDED` — Partial or full refund issued to customer

**PaymentFailureReason** (enum):
- `INSUFFICIENT_FUNDS`
- `CARD_DECLINED`
- `EXPIRED_CARD`
- `INVALID_AMOUNT`
- `FRAUD_DETECTED`
- `PROVIDER_ERROR` — Transient; eligible for retry
- `NETWORK_ERROR` — Transient; eligible for retry
- `UNKNOWN` — Inspect provider response

**PaymentMethodType** (enum):
- `CARD` — Credit/debit card (Visa, Mastercard, Amex, Discover)
- `BANK_TRANSFER` — Direct ACH, SEPA, wire
- `DIGITAL_WALLET` — Apple Pay, Google Pay, PayPal (tokenized)

**PaymentMethodToken**:
- Never stored raw PAN/account numbers; always tokenized via payment processor
- Token references: `provider_id.token_hash` (e.g., "stripe.tok_visa123")
- Immutable once captured

---

## Lifecycle & State Transitions

```
CREATED
  ↓ [authorize()]
AUTHORIZED
  ├─ [capture()]  → CAPTURED → [settle()] → SETTLED
  ├─ [fail()]     → FAILED
  └─ [timeout 7d] → FAILED (auth expires, varies by scheme)

CAPTURED
  ├─ [settle()]   → SETTLED
  ├─ [fail()]     → FAILED
  └─ [refund()]   → REFUNDED (creates Refund aggregate, original stays CAPTURED)

SETTLED
  └─ [refund()]   → REFUNDED (creates Refund aggregate)

FAILED / REFUNDED (terminal; no further state changes)
```

---

## Business Rules & Constraints

### Authorization

1. **One authorization per payment ID**: re-authorizing same payment ID returns cached result or fails
2. **Idempotency**: Same idempotencyKey within 24 hours always returns same result (idempotent retriable)
3. **No double-charge**: Attempting to authorize twice with same key returns original result
4. **Authorization hold**: Funds held for 3–7 days (card scheme dependent); must capture within window or hold expires

### Capture

1. **Capture after authorization only**: Capture requires prior AUTHORIZED status
2. **Partial capture allowed**: Capture 50 USD of 100 USD authorized; subsequent capture can claim remaining 50 USD (explicit multi-capture or leave for refund)
3. **No capture after authorization expires**: After hold expires (7 days typical), capture fails; re-authorize required
4. **Idempotent capture**: Capturing same amount twice (same idempotencyKey) succeeds once, subsequent attempts return cached result

### Refund

1. **Refund only captured/settled payments**: Cannot refund AUTHORIZED-only (no funds actually taken yet)
2. **Partial refund allowed**: Refund 30 USD of 100 USD captured; track amountRefunded incrementally
3. **No refund exceeding captured amount**: Prevent accidental over-refunds
4. **Refund within time limit**: Varies by scheme (typically 60–180 days; some merchants have longer windows)

### Amount Precision

1. **Store as cents (integer) or use BigDecimal**: Never float/double for money (rounding errors)
2. **Validation**: amount > 0, amount ≤ 999,999.99 (card scheme limit, 6 digits before decimal)
3. **Captured ≤ Authorized**: amountCaptured cannot exceed amount (for card holds)
4. **Refunded ≤ Captured**: amountRefunded cannot exceed amountCaptured

---

## Events

Emitted by this aggregate and persisted in MongoDB event store; consumed by Kafka → read projections & external systems.

### PaymentCreated
```java
{
  PaymentId paymentId;
  String idempotencyKey;
  MerchantId merchantId;
  CustomerId customerId;
  Money amount;
  PaymentMethodToken paymentMethodToken;
  String orderReference;
  String description;
  Instant createdAt;
}
```
**Purpose**: Initializes payment record; signals intent to charge.

### PaymentAuthorized
```java
{
  PaymentId paymentId;
  String authorizationCode;
  String providerTransactionId;
  Instant authorizedAt;
  Instant authorizationExpiresAt;  // e.g., +7 days from now
}
```
**Purpose**: Authorization succeeded; funds reserved (or accepted, for bank transfers).

### PaymentCaptured
```java
{
  PaymentId paymentId;
  Money amountCaptured;            // Incremental (50 of 100 on first, 50 on second for partial multi-capture)
  String providerTransactionId;    // May differ from auth if explicit capture call made
  Instant capturedAt;
}
```
**Purpose**: Funds actually debited; payment ready for settlement batch.

### PaymentFailed
```java
{
  PaymentId paymentId;
  PaymentFailureReason reason;
  String errorMessage;
  Instant failedAt;
  int retryCount;
}
```
**Purpose**: Authorization or capture failed; payment will not settle. Triggers merchant notification.

### PaymentSettled
```java
{
  PaymentId paymentId;
  Instant settledAt;
  String settlementBatchId;        // Links to Settlement aggregate
}
```
**Purpose**: Settlement processor confirmed funds deposited to merchant bank.

### PaymentRefunded
```java
{
  PaymentId paymentId;
  RefundId refundId;               // Separate Refund aggregate handles details
  Money amountRefunded;            // Incremental
  String refundReason;
  Instant refundedAt;
}
```
**Purpose**: Refund issued. Original Payment status remains CAPTURED (or SETTLED); Refund tracks the reversal.

---

## Aggregate Behavior (Simplified Commands → Events)

### authorize(AuthorizePaymentCommand)
**Preconditions**: status = CREATED, no prior authorization for this PaymentId
**Side effects**: 
  - Call payment provider (PSP) API
  - Store authorizationCode, providerTransactionId
  - Emit PaymentAuthorized event
  - Set idempotencyKey → cached result (prevent double charge)
**Post-conditions**: status = AUTHORIZED, authorizationExpiresAt set to +7 days

### capture(CapturePaymentCommand)
**Preconditions**: status = AUTHORIZED, authorizationExpiresAt > now, captureAmount ≤ (amount - amountCaptured)
**Side effects**:
  - Call PSP capture API (or automatic if PSP auth = capture model)
  - Increment amountCaptured
  - Emit PaymentCaptured event
**Post-conditions**: status = CAPTURED (if amountCaptured == amount, else stay AUTHORIZED for multi-capture)

### fail(FailPaymentCommand)
**Preconditions**: status in [CREATED, AUTHORIZED, CAPTURED]
**Side effects**:
  - Set failureReason
  - Emit PaymentFailed event
  - Alert merchant (via Kafka)
**Post-conditions**: status = FAILED (terminal)

### refund(RefundPaymentCommand) → creates Refund aggregate
**Preconditions**: status in [CAPTURED, SETTLED], refundAmount ≤ (amountCaptured - amountRefunded)
**Side effects**:
  - Create new RefundId
  - Emit PaymentRefunded event (references Refund aggregate)
  - Refund aggregate handles PSP refund API call
**Post-conditions**: amountRefunded incremented; Payment status remains CAPTURED/SETTLED

---

## PCI-DSS Compliance

1. **Never store raw PAN/CVV**: Only tokenized references stored
2. **Mask PAN in logs/UI**: Use maskedPan ("****1234") for display
3. **Audit trail**: All events immutably logged via event store
4. **Idempotency key prevents duplicate charges**: Protects against accidental retries
5. **Error responses never leak sensitive data**: No card details in failure messages

---

## Idempotency & Retry Strategy

| Scenario | Idempotency Approach | Cache TTL |
|---|---|---|
| authorize(idempotencyKey=X) twice | Return cached authorizationCode + PaymentAuthorized event | 24 hours |
| capture(idempotencyKey=X) twice | Return cached capture result | 24 hours |
| Network timeout on authorize call | Retry with same idempotencyKey; PSP deduplicates | TTL protects retries |

**Implementation**:
- Idempotency repository (Mongo coll): `idempotencyKey → PaymentId + resultEvent`
- Query on incoming request; if found & still valid, return cached result
- Prevents duplicate database mutations if client retries

---

## Related Aggregates

- **Refund**: Separate aggregate (contains refund-specific state, events, lifecycle)
- **Capture**: Explicit capture (for 3-step auth-capture-settle flow) may be its own aggregate or bundled in Payment
- **Settlement**: Batch that groups captured payments; separate aggregate
- **Merchant**: Owns payment method tokenization rules, dispute handling, settlement preferences

---

## Common Queries (Read Model via PostgreSQL)

- Payment status by PaymentId
- Payments by MerchantId + date range + status
- Refunds by PaymentId
- Disputes / chargebacks by PaymentId
- Settlement reconciliation (payments by SettlementBatchId)

These are fulfilled by read projections (event handlers → PostgreSQL tables) updated asynchronously from Kafka.
