# Development Workflow

How a task moves from backlog to merged, and which role acts at each step. The task ledger in `tasks/` is the single source of truth for who is doing what; the rules here exist so multiple agents can work in parallel without stepping on each other.

## The loop

```
backlog -> in-progress -> review -> done
  claim       verify      approve + merge
```

Roles per stage: `architect` before claiming (design, when needed), `backend-engineer` and `test-engineer` during in-progress, `code-reviewer` plus `security-reviewer` (when security-relevant) at review.

1. **Design (when needed).** Features without an existing design in `architecture/` or `knowledge/domain/` go to the `architect` agent first. Output: a design doc, and an ADR if a decision was made. Small fixes skip this step.
2. **Claim.** Set `owner` and `status: in-progress` in the task file, commit that change directly to `main`. The ledger commit is the lock: check `tasks/TASKS.md` for overlapping in-progress scopes before claiming. This is the one exception to the pull-request rule below — it's a metadata-only coordination signal, not code, and needs to land immediately to actually function as a lock.
3. **Implement.** Work happens on `task/T-NNN-slug`, branched from current `main`. The `backend-engineer` agent implements; the `test-engineer` agent is pulled in when coverage or test infrastructure needs dedicated work. Commits follow Conventional Commits.
4. **Verify.** `mvn verify` green on the branch is the entry ticket to review; it includes the JaCoCo floor and the ArchUnit architecture rules. Set `status: review`, append a handoff-log entry describing what was done and anything touched outside scope, push the branch, and open a pull request against `main` (`gh pr create`). No code reaches `main` by any other path — not a local `git merge`, not a direct push — regardless of which tool or agent produced it. `backend-engineer` opens the PR as part of handing off; it does not merge it.
5. **Review.** `code-reviewer` reviews the PR diff; `security-reviewer` additionally reviews anything touching auth, payment endpoints, logging, or dependencies (T-004 style tasks always get both). Blockers send the task back to `in-progress` with findings in the handoff log; the PR stays open until the fix is pushed and review passes again.
6. **Merge.** Once every required reviewer has approved, the orchestrating Claude Code session merges the PR itself (`gh pr merge --merge --delete-branch`) — this authority is granted here as standing policy, not negotiated per PR, so merging doesn't wait on a separate human go-ahead once approval is on record. After the merge, push a follow-up commit on `main` (not the deleted task branch) that sets `status: done` in the task file and updates `TASKS.md`; the merge commit itself never carries the ledger change, since it's just whatever the PR's own commits contained.

## Concurrency rules

- One task, one owner, one branch. Never two agents on one branch.
- Tasks with overlapping scope sections must not be in progress at the same time; the second one waits or the scopes get renegotiated.
- Every code change reaches `main` exclusively through a reviewed, approved, merged pull request — no agent, Claude Code or OpenCode, ever lands code on `main` by a local `git merge` or a direct `git push`. The one exception is the ledger claim/close commits in steps 2 and 6, which are metadata only, not code, and are pre-authorized to go straight to `main` because they function as a lock. "No agent merges or pushes *code* to `main` outside the pull-request flow" is the actual rule; it is not a blanket ban on every commit ever landing on `main` without a PR.
- `main` is protected server-side by a GitHub ruleset (id `19586882`: blocks direct updates/deletion/non-fast-forward, requires 1 approving review, zero bypass actors) — that ruleset, not this document, is the authoritative enforcement layer; the agent-config guardrails in `.opencode/opencode.json` and `.claude/settings.json` are defense-in-depth on top of it, not a substitute for it. Verify the ruleset's `conditions.ref_name` actually scopes to `main` before relying on it (`gh api repos/{owner}/{repo}/rulesets/19586882`) — an empty `include` list is easy to misread as "applies everywhere" when it may mean the opposite.
- Rebase the task branch on `main` before requesting review if `main` moved.

## When things go wrong

- A blocked task gets `status: blocked` plus a handoff-log entry naming the blocker; it does not sit silently in `in-progress`.
- Learnings that will save future work (framework gotchas, dependency issues, decisions) go to `.claude/knowledge/` or `knowledge/decisions/` per the project-knowledge rule, before the task is closed.
- Notable events worth tracing later get a dated line in `.claude/history/YYYY-MM.md`.
