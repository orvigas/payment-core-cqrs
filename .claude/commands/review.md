---
description: Review a task branch against governance and merge on approval
argument-hint: task id, e.g. T-003 (defaults to the current branch's task)
---

Review the branch for task $ARGUMENTS per `workflow/DEVELOPMENT_WORKFLOW.md`.

1. Determine the task branch (`task/T-NNN-*`) and diff it against `main`.
2. Run the code-reviewer agent on the diff. If the change touches auth, payment endpoints, logging, configuration secrets, or dependencies, also run the security-reviewer agent.
3. Confirm `mvn verify` is green on the branch; a red build is an automatic request-changes.
4. On request-changes: set the task back to `in-progress`, record the findings in the handoff log, and report them.
5. On approval: merge with `--no-ff`, set `status: done`, update `tasks/TASKS.md`, delete the branch, and record the verdict in the handoff log.
6. Before closing, apply the project-knowledge rule: if the task produced a reusable learning, capture it in `.claude/knowledge/` and index it.
