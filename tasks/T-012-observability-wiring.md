---
id: T-012
title: Observability wiring (metrics, tracing, structured logs)
status: backlog
owner: none
branch: none
depends-on: []
---

# T-012: Observability wiring (metrics, tracing, structured logs)

## Goal

The app actually emits the metrics, traces, and structured logs that `monitoring/` is already configured to receive. Right now the Prometheus/Grafana/Jaeger stack runs in `docker-compose.yml` with nothing feeding it — no custom Micrometer metrics, no Brave/Zipkin spans, no structured logging exist in `src/main` today.

## Scope

- `src/main/java/com/orvigas/observability/`
- `src/main/resources/application.yml`
- `src/main/resources/logback-spring.xml` (or equivalent, if not already present)

## Acceptance criteria

- [ ] Micrometer metrics exposed via Actuator's Prometheus endpoint, matching what `monitoring/prometheus/` expects to scrape
- [ ] Tracing exported through Brave/Zipkin to the `otel-collector` — never directly to Jaeger, per `.claude/CLAUDE.md` — and a sample request's trace is confirmed visible in the Jaeger UI
- [ ] `pom.xml` carries all three zipkin-reporter artifacts together (`.claude/CLAUDE.md` calls out that spans are silently dropped if any one is missing) — verify, don't assume
- [ ] Structured JSON logging via SLF4J, with `governance/SECURITY_POLICY.md` redaction rules applied (no tokens, secrets, or PII in log output)
- [ ] Trace and span ids appear in structured log lines so an on-call engineer can jump from a log line to the matching Jaeger trace
- [ ] Auth context (JWT claims) needed for logging/tracing propagates via Reactor context, consistent with the existing reactive security chain — not thread-locals
- [ ] At least one Grafana dashboard under `monitoring/` reflects a real application-emitted metric, not only infrastructure metrics
- [ ] `mvn verify` passes

## Notes

This task is infrastructure plumbing, not a specific feature's instrumentation — keep it to the shared observability configuration and a small number of representative metrics/spans (for example, on the payment command path). Per-feature instrumentation as new features land is expected to follow this pattern, not be re-litigated each time.

## Handoff log
