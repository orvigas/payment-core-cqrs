---
id: T-NNN
title: Short imperative title
status: backlog
owner: none
branch: none
depends-on: []
---

# T-NNN: Title

## Goal

What exists when this task is done, in one or two sentences. Outcome, not activity.

## Scope

Paths this task may create or modify. Anything outside this list needs the change flagged in the handoff note.

- `services/...`
- `src/...`

## Acceptance criteria

Concrete, checkable statements. Reference domain invariants from `knowledge/domain/` and gates from `governance/QUALITY_GATE.md` where they apply.

- [ ] ...
- [ ] `mvn verify` passes (coverage floor and ArchUnit rules included)
- [ ] Failure paths tested, not only the happy path

## Notes

Context the implementer needs: relevant design docs, ADRs, gotchas from `.claude/knowledge/`, open questions.

## Handoff log

Dated entries appended at each status change: what was done, what remains, review findings.
