# Security Policy

Security requirements for Payment Core. This is a payment system: assume every endpoint is an attack target and every data element is sensitive until classified otherwise. Baseline references: OWASP Top 10 (2021), OWASP ASVS 4.0 Level 2, and the OWASP API Security Top 10. Where PCI DSS applies to card data handling, its requirements override anything weaker in this document.

## 1. Authentication

- Stateless JWT bearer tokens, signed with HMAC-SHA256 via JJWT. Verification uses `parseSignedClaims()` exclusively — never an unverified parse, and the `alg` header is never trusted to select the algorithm (prevents `none`/key-confusion attacks).
- Signing keys are at least 256 bits, generated from a CSPRNG, injected via environment/secret manager, and rotated on a schedule and immediately on suspicion of exposure. Key IDs (`kid`) support overlap during rotation.
- Access tokens are short-lived (target: 15 minutes). Refresh, if introduced, uses rotating single-use refresh tokens with server-side revocation.
- Passwords are hashed with BCrypt (current work factor 10; revisit yearly against hardware cost). Password comparison and token validation use constant-time comparisons — the library's, never hand-rolled.
- Authentication failures return a generic 401 without distinguishing "unknown user" from "wrong password", and without timing differences that leak the distinction.

## 2. Authorization

- Every non-public endpoint enforces authorization in the reactive security chain (`ServerHttpSecurity`); there are no endpoints protected only by obscurity or gateway configuration.
- Object-level checks on every resource access: a caller may only read or act on payments they own, verified server-side per request (OWASP API #1, broken object level authorization). The resource ID coming from the caller is never trusted as proof of access.
- Deny by default: new routes are unauthenticated only by explicit allowlist entry, reviewed as a security-relevant change.
- Auth context propagates via the Reactor context, never thread-locals — a thread-local on an event loop leaks one user's identity into another's request.

## 3. Input Handling and Injection

- All input validated at the boundary with Jakarta Bean Validation (types, ranges, formats, lengths) before any business logic runs. Monetary amounts are validated for scale, sign, and currency against an allowlist.
- Database access goes through Spring Data R2DBC with parameterized bindings only. String-concatenated SQL is banned without exception, including in migrations that take runtime input (which should not exist).
- Deserialization only into known, typed records — never polymorphic deserialization of caller-controlled type names.
- Output encoding on anything reflected back to a client; error responses follow RFC 7807 and never echo raw input unsanitized.

## 4. Secrets Management

- No secrets in source, config files under version control, Docker images, or logs. Local development uses env files that are gitignored; deployed environments use the platform secret store.
- CI runs gitleaks on every push. A committed secret is considered compromised the moment it is pushed: rotate first, clean history second. Reverting the commit does not un-leak it.
- Third-party credentials (payment provider, broker) are scoped to least privilege and rotated on personnel changes.

## 5. Data Protection

- Classify data on ingestion: card data (if ever handled) follows PCI DSS — prefer tokenization via the provider so raw PANs never enter this system; personal data (names, emails) is minimized, encrypted at rest, and never used in test fixtures with real values.
- TLS 1.2+ for every network hop in deployed environments, including service-to-broker and service-to-database. Plaintext is acceptable only inside the local docker-compose network.
- Logs contain identifiers, never sensitive values: log the payment ID, not the card number; the user ID, not the email; the token's `jti`, not the token. The logging encoder masks known sensitive field names as a backstop, but the primary control is not logging them.
- Events on Kafka topics are treated as long-lived records: no secrets or raw personal data in event payloads, because retention and replay make deletion impractical (GDPR erasure is handled via crypto-shredding or reference indirection, decided before the first personal field ships).

## 6. Availability and Abuse Protection

- Resilience4j rate limiting on login and payment initiation, keyed per caller identity (per IP pre-auth), with limits tuned from load-test baselines.
- Request body size limits and pagination caps on every collection endpoint prevent resource-exhaustion via oversized input.
- Idempotency keys on payment writes ensure a retried request cannot double-charge — an availability and correctness control.

## 7. Supply Chain

- Dependency vulnerability scanning in CI (see `QUALITY_GATE.md` §4): CVSS >= 7.0 with an available fix fails the build; everything else is tracked with an owner and due date.
- A CycloneDX SBOM ships with every release so exposure to a newly disclosed CVE is answerable in minutes, not by grepping poms.
- New dependencies require review: actively maintained, from the official coordinates (typosquat check), and pulled through the pinned-version policy — no ranges. Build plugins are held to the same standard as runtime dependencies.
- CI itself is least-privilege: build jobs cannot read deployment secrets; release signing happens in a separate, gated stage.

## 8. Security in the Development Process

- Threat modeling (STRIDE-lite) for every new externally reachable surface: new endpoint, new topic consumer, new third-party integration. Output is a short section in the design note, not a separate ceremony.
- SAST and secret scanning gate every merge; DAST (ZAP baseline scan) runs against the composed stack before release.
- Security-relevant changes — auth chain, token handling, rate limits, migration of sensitive columns — require a second reviewer with security context.
- Stack traces, framework banners, and actuator internals are never exposed publicly: actuator endpoints beyond `/actuator/health` and `/actuator/prometheus` are unexposed or authenticated, and error responses carry a correlation ID instead of a trace.

## 9. Incident Response

- A suspected compromise (leaked key, anomalous charging pattern, dependency backdoor) is triaged immediately: rotate affected credentials, capture evidence (logs, traces) before it ages out of retention, then remediate.
- Every incident produces a blameless write-up in `.claude/knowledge/` answering: what happened, root cause, detection gap, fix, and prevention. The fix includes a detection improvement, not just a patch.

## 10. Reporting a Vulnerability

Report suspected vulnerabilities privately to the maintainer (orvigas@gmail.com) rather than opening a public issue. Include reproduction steps and impact assessment. Acknowledged within 48 hours; fix timelines follow severity (critical: 7 days, high: 30 days). Good-faith research against local/test deployments is welcome; testing against live payment flows is not authorized.
