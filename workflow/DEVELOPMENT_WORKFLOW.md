# Development Workflow

How a task moves from backlog to merged, and which role acts at each step. The task ledger in `tasks/` is the single source of truth for who is doing what; the rules here exist so multiple agents can work in parallel without stepping on each other.

## The loop

```
backlog -> in-progress -> review -> done
  claim       verify      approve + merge
```

Roles per stage: `architect` before claiming (design, when needed), `backend-engineer` and `test-engineer` during in-progress, `code-reviewer` plus `security-reviewer` (when security-relevant) at review.

1. **Design (when needed).** Features without an existing design in `architecture/` or `knowledge/domain/` go to the `architect` agent first. Output: a design doc, and an ADR if a decision was made. Small fixes skip this step.
2. **Claim.** Set `owner` and `status: in-progress` in the task file, commit that change directly to `main`. The ledger commit is the lock: check `tasks/TASKS.md` for overlapping in-progress scopes before claiming.
3. **Implement.** Work happens on `task/T-NNN-slug`, branched from current `main`. The `backend-engineer` agent implements; the `test-engineer` agent is pulled in when coverage or test infrastructure needs dedicated work. Commits follow Conventional Commits.
4. **Verify.** `mvn verify` green on the branch is the entry ticket to review; it includes the JaCoCo floor and the ArchUnit architecture rules. Set `status: review`, append a handoff-log entry describing what was done and anything touched outside scope.
5. **Review.** `code-reviewer` reviews the branch diff; `security-reviewer` additionally reviews anything touching auth, payment endpoints, logging, or dependencies (T-004 style tasks always get both). Blockers send the task back to `in-progress` with findings in the handoff log.
6. **Merge.** On approval, merge the branch to `main` (no fast-forward, so task boundaries stay visible in history), set `status: done`, update `TASKS.md`, delete the branch.

## Concurrency rules

- One task, one owner, one branch. Never two agents on one branch.
- Tasks with overlapping scope sections must not be in progress at the same time; the second one waits or the scopes get renegotiated.
- Only ledger updates (claim, status changes) are committed directly to `main`; all code goes through a task branch and review.
- Rebase the task branch on `main` before requesting review if `main` moved.

## When things go wrong

- A blocked task gets `status: blocked` plus a handoff-log entry naming the blocker; it does not sit silently in `in-progress`.
- Learnings that will save future work (framework gotchas, dependency issues, decisions) go to `.claude/knowledge/` or `knowledge/decisions/` per the project-knowledge rule, before the task is closed.
- Notable events worth tracing later get a dated line in `.claude/history/YYYY-MM.md`.
