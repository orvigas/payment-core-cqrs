# Quality Gate

The quality gate is the automated definition of "done." Code merges to the main branch only when every check below passes in CI. The gate is not advisory: a red gate blocks the merge, and disabling a check to get a change in is treated as an incident, not a workaround.

## 1. Build and Compilation

- `mvn verify` succeeds from a clean checkout with no local state. If it only works on one machine, it is broken.
- Zero compiler warnings. Deprecation and unchecked warnings are fixed or explicitly suppressed with an inline justification.
- Reproducible builds: dependency versions are pinned via the Boot BOM or explicit properties in `pom.xml` — no version ranges, no `LATEST`.

## 2. Tests

- All unit and integration tests pass. Flaky tests are quarantined the day they flake and fixed or deleted within the sprint — a test that is sometimes red trains everyone to ignore red.
- **Coverage floor: 95% instruction coverage**, enforced by JaCoCo in `mvn verify`. This is a floor, not a target; a class at 95% with untested failure paths still fails review.
- Test distribution follows the pyramid in `ARCHITECTURE_RULES.md` §11: unit tests with `StepVerifier` for reactive logic, Testcontainers Postgres for persistence, embedded Kafka for messaging. No H2, no mocked repositories standing in for SQL behavior.
- Every bug fix ships with a test that fails on the pre-fix code. No regression test, no merge.
- Mutation testing (PIT) runs on the payment and charge packages nightly; a falling mutation score on money-path code is triaged like a failing test.

## 3. Static Analysis

| Check | Tool | Policy |
|---|---|---|
| Formatting | Spotless (Google Java Format) | Must be clean; `mvn spotless:apply` before commit |
| Style | Checkstyle | Zero violations at severity `error` |
| Bug patterns | ErrorProne + SpotBugs | Zero findings at `HIGH`; lower severities triaged in review |
| Maintainability | SonarQube quality profile | No new code smells rated `critical`+; new-code duplication under 3% |
| Architecture | ArchUnit tests | Layer direction, no `.block()` on the request path, no field injection — enforced as unit tests, not review comments |

Suppressions of any static-analysis finding require an inline comment stating why and a reviewer's approval.

## 4. Security Checks (summary — full policy in SECURITY_POLICY.md)

- Dependency vulnerability scan (OWASP Dependency-Check or equivalent, e.g. Trivy/Grype in CI): build fails on CVSS >= 7.0 with a fix available. Lower severities open a tracked issue.
- Secret scanning (gitleaks) on every push; a leaked credential fails the build and triggers rotation regardless of whether the commit is reverted.
- SBOM (CycloneDX) generated on every release build and published with the artifact.
- SAST findings at high severity block the merge.

## 5. Database Migrations

- Every schema change is a new Flyway migration; CI verifies checksums against the migration history and fails on any edit to an applied migration.
- Migrations run green against a fresh Testcontainers Postgres 15 (empty database) and against a database at the previous release's schema (upgrade path).
- Destructive migrations (drop, type narrowing) require an explicit rollback note in the migration header comment and reviewer sign-off.

## 6. API Contract

- The OpenAPI spec is generated in CI and diffed against the previous version; a breaking change (removed field, narrowed type, new required parameter) fails the build unless the endpoint version was bumped.
- Every new endpoint appears in the spec with request/response schemas and error responses — an undocumented endpoint is an incomplete endpoint.

## 7. Review

- At least one approving review from someone who did not author the change. Money-path changes (charging, saga compensation, event schema) require two.
- The author states in the PR description what was tested and how; "CI is green" alone is not a test description.
- Review scope includes tests: a reviewer who approves untested failure paths owns them alongside the author.

## 8. Observability Readiness

A feature passes the gate only when it can be operated:

- New operations expose outcome counters and latency timers via Micrometer.
- New request paths carry trace spans through the OTel collector pipeline.
- New failure modes log structured, correlation-ID-tagged entries at the level an on-call engineer would search for (`WARN` for degraded, `ERROR` for action-required).

## 9. Performance

- K6 load scenarios in `loadtest/` run against release candidates. Regression thresholds: p95 latency and error rate must stay within 10% of the previous release's baseline under the steady-state scenario.
- A change expected to alter performance characteristics (new query, new external call, new serialization) includes a before/after measurement in the PR.

## 10. Waivers

There is exactly one escape hatch: a time-boxed waiver, recorded in `.claude/history/` with the reason, the owner, and an expiry date, approved by the tech lead. An expired waiver fails the build. There are no permanent waivers.
