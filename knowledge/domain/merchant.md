# Merchant

A Merchant is the business that accepts payments through the platform. It is the anchor for authorization decisions (may this merchant charge at all?), fee calculation, and payouts. Every [[payment]] belongs to exactly one merchant, and every [[settlement]] pays exactly one merchant.

Merchant is its own aggregate. It changes on a completely different cadence than payments (onboarding, compliance reviews, configuration edits) and is referenced by id from the payment flow rather than loaded into it. Command handlers on the payment side validate merchant status against a projection, accepting the small staleness window; a merchant suspended mid-flight is caught by the next command in the chain.

## Properties

| Property | Type | Meaning |
| --- | --- | --- |
| `merchantId` | UUID | Aggregate identifier, referenced by payments and settlements. |
| `legalName` / `tradingName` | String | Registered company name vs the name shown on customer statements. Both are needed; statement descriptors that don't match the trading name drive chargebacks from customers who don't recognize the charge. |
| `status` | enum | `ONBOARDING`, `ACTIVE`, `SUSPENDED`, `CLOSED`. Only `ACTIVE` merchants can initiate payments. `SUSPENDED` blocks new payments but still allows refunds and settlements, because freezing customer refunds during a compliance review is both a UX and a regulatory problem. |
| `kybStatus` | enum | Know-Your-Business verification state: `PENDING`, `VERIFIED`, `REJECTED`, `REVIEW_REQUIRED`. Regulatory prerequisite for `ACTIVE`; periodic re-verification can pull a verified merchant back into review. |
| `country` / `mcc` | ISO 3166 / MCC code | Incorporation country and Merchant Category Code. Both drive scheme rules, fee tiers, and which payment methods are available. |
| `supportedCurrencies` | set | Currencies the merchant may charge in. Validated at payment initiation. |
| `feeSchedule` | value object | Pricing applied to this merchant's transactions, typically percentage plus fixed amount per capture, with per-method overrides. Versioned: a fee change applies from an effective date, and historical settlements keep the schedule that was active at the time. |
| `settlementAccount` | value object | Bank account for payouts (IBAN or local format, account holder, verification status). Changing it is the highest-risk operation on this aggregate: it is the classic account-takeover target, so changes require re-verification and delay the next payout until verified. |
| `settlementSchedule` | value object | Payout cadence (daily, weekly) and delay (for example T+2). Feeds [[settlement]] period handling. |
| `reserve` | value object | Rolling reserve configuration: a percentage of settlement withheld for a period to cover refunds and chargebacks after a merchant churns or fails. Covers negative settlements. |
| `riskLevel` | enum | `LOW`, `MEDIUM`, `HIGH`. Set by onboarding and updated by monitoring; drives reserve size, payout delay, and manual review thresholds. |
| `apiCredentialsRef` | reference | Pointer to the merchant's API credentials in the secrets store. The domain model holds the reference only; key material never enters events or projections. |
| `createdAt` / `activatedAt` / `closedAt` | Instant | Lifecycle audit timestamps. |

## Behavior

| Method | Preconditions | Effect |
| --- | --- | --- |
| `register(...)` | Legal name, country, and contact present | Emits `MerchantRegistered`, status `ONBOARDING`. |
| `completeKyb(result)` | Status `ONBOARDING` or `REVIEW_REQUIRED` | Emits `MerchantKybCompleted` or `MerchantKybRejected`. |
| `activate()` | KYB `VERIFIED`, settlement account verified, fee schedule assigned | Emits `MerchantActivated`. The gate every payment-side check relies on. |
| `suspend(reason)` | Status `ACTIVE` | Emits `MerchantSuspended` with a structured reason (risk, compliance, merchant request). |
| `reinstate()` | Status `SUSPENDED`, cause resolved | Emits `MerchantReinstated`. |
| `updateSettlementAccount(account)` | Any active status | Emits `MerchantSettlementAccountChanged`; account enters `PENDING_VERIFICATION` and payouts pause until verified. |
| `updateFeeSchedule(schedule, effectiveFrom)` | Effective date in the future | Emits `MerchantFeeScheduleChanged`. Never retroactive. |
| `close()` | No open [[settlement]]; reserve released or paid out | Emits `MerchantClosed`. Terminal; historical data is retained for the regulatory period, never deleted. |

## Invariants

- Only `ACTIVE` merchants initiate payments; refunds and settlements survive suspension.
- `ACTIVE` requires verified KYB and a verified settlement account, always.
- Fee schedule changes are forward-only; settled history is immutable.
- A merchant with open settlements or a held reserve cannot be closed.

## Design notes

- Bank details, tax ids, and contact data are classified data; events carry them encrypted or as references per SECURITY_POLICY, and log redaction applies.
- The payment flow needs merchant status and currency support at high read volume, so those fields get a dedicated lightweight projection separate from the full merchant profile view.
