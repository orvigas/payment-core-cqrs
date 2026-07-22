---
id: T-004
title: Reactive JWT security chain
status: in-progress
owner: opencode
branch: task/T-004-jwt-security-chain
depends-on: [T-001]
---

# T-004: Reactive JWT security chain

## Goal

Stateless JWT authentication on the reactive chain: `ServerHttpSecurity` configuration, JJWT issuance and validation, BCrypt password handling, and Resilience4j rate limiting on the login endpoint.

## Scope

- `src/main/java/com/orvigas/security/`
- `src/main/java/com/orvigas/auth/` (login endpoint)
- `src/test/java/com/orvigas/security/`, `src/test/java/com/orvigas/auth/`
- `src/main/resources/application.yml` (security and rate-limiter sections only)

## Acceptance criteria

- [ ] Token parsing uses `parseSignedClaims()` exclusively; tampered and expired tokens are rejected with tests proving it
- [ ] Auth context propagates via Reactor context; no thread-local access anywhere on the chain
- [ ] HMAC secret comes from configuration; no key material in code, logs, or error responses
- [ ] Login is rate-limited via Resilience4j with a test hitting the limit
- [ ] Security-relevant failures return RFC 7807 problem details without leaking internals
- [ ] `mvn verify` passes; security-reviewer sign-off recorded in the handoff log

## Notes

Requirements from `governance/SECURITY_POLICY.md` and the security section of `governance/TECH_STACK.md`. This task gates any externally reachable endpoint work.

## Handoff log
