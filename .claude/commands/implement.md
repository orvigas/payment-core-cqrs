---
description: Claim a task from the ledger and implement it end to end
argument-hint: task id, e.g. T-003
---

Implement task $ARGUMENTS following `workflow/DEVELOPMENT_WORKFLOW.md`.

1. Read the task file in `tasks/`. Verify its dependencies are `done` and no in-progress task overlaps its scope; stop and report if either fails.
2. Claim it: set `owner` and `status: in-progress`, update `tasks/TASKS.md`, commit to `main`.
3. Branch: `task/$ARGUMENTS-<slug>` from `main`.
4. Implement using the backend-engineer agent's rules; involve the test-engineer agent for coverage-heavy work. Stay inside the task's scope section.
5. Run `mvn verify` and iterate until green.
6. Set `status: review`, append a handoff-log entry (what was done, what remains, anything touched outside scope), commit on the branch.
7. Report the branch name and a summary; do not merge — that happens after `/review`.
