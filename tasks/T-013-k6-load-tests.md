---
id: T-013
title: K6 load test scenarios for the payment API
status: backlog
owner: none
branch: none
depends-on: [T-008]
---

# T-013: K6 load test scenarios for the payment API

## Goal

`loadtest/` exists with runnable K6 scenarios exercising the payment REST API against the local docker-compose stack, with pass/fail thresholds — closing the gap between what `.claude/CLAUDE.md` already documents (`loadtest/payment-load-test.js`, `loadtest/scenarios/`) and what actually exists (nothing).

## Scope

- `loadtest/`

## Acceptance criteria

- [ ] `loadtest/payment-load-test.js` exercises the payment lifecycle end to end (initiate, capture, refund) against a running local stack
- [ ] At least one ramping-load scenario and one spike/stress scenario under `loadtest/scenarios/`
- [ ] Thresholds defined (p95 latency, error rate) so a run produces a pass/fail verdict, not just raw numbers
- [ ] A short README (or top-of-file comment) documents how to run it against `docker-compose up -d`; explicitly not part of `mvn verify` — this is a manual/CI-triggered step, not a build gate
- [ ] A baseline run's results are recorded in `.claude/knowledge/` or `.claude/history/` for future comparison

## Notes

Depends on T-008 existing — there is no payment HTTP endpoint to load test until then.

## Handoff log
