# Multi-Agent Development Standard

## Purpose
Define mandatory rules for safe parallel development using Git worktrees and an Orchestrator.

## 1. Core Principle
- Every agent uses exactly one Git worktree and one feature branch.
- No shared working directories.
- Only the Orchestrator coordinates integration.

## 2. Repository Layout
```text
repo/
worktrees/
  agent-auth/
  agent-orders/
  agent-frontend/
```

## 3. Naming Conventions
- Branch: `feature/<task>`
- Worktree: `worktrees/<agent-name>`
- Agent: unique identifier
- Task: one logical feature

## 4. Worktree Lifecycle
1. Create feature branch.
2. Create worktree.
3. Assign agent.
4. Execute task.
5. Validate.
6. Merge.
7. Delete worktree.
8. Delete merged branch.

## 5. Agent Startup Checklist
- Verify repository root.
- Verify assigned worktree.
- Verify assigned branch.
- Verify clean status.
- Verify task assignment.

## 6. Agent Rules
- Modify only assigned files.
- Commit only to assigned branch.
- Never merge.
- Never switch branches.
- Stop on verification failures.

## 7. Orchestrator Responsibilities
- Create branches/worktrees.
- Maintain Agent→Branch→Worktree→Task mapping.
- Prevent overlapping work.
- Schedule rebases.
- Merge incrementally.
- Run CI after every merge.

## 8. Safety Checks
Before writes:
- pwd
- git rev-parse --show-toplevel
- git branch --show-current
- git worktree list

Before commit:
- git status
- review staged files
- verify task scope

## 9. Merge Policy
- Agents never merge.
- Orchestrator merges one branch at a time.
- Run build, tests, lint, static analysis, security scan, smoke tests after every merge.

## 10. Conflict Resolution
1. Identify overlap.
2. Understand both changes.
3. Preserve valid behavior.
4. Retest.
5. Commit dedicated merge resolution.

## 11. Recovery
If wrong branch/worktree or unexpected changes:
- Stop.
- Report.
- Await orchestrator.
- No automatic recovery.

## 12. CI/CD Gates
- Build passes
- Unit tests
- Integration tests
- Formatting
- Lint
- Static analysis
- Coverage threshold
- Security scan

## 13. Git Commands
```bash
git worktree add ../worktrees/agent-auth -b feature/auth
git worktree list
git status
git branch --show-current
git fetch origin
git rebase origin/main
git worktree remove ../worktrees/agent-auth
git branch -d feature/auth
```

## 14. Anti-Patterns
- Two agents editing same file.
- Shared worktree.
- Batch merging many branches.
- Long-lived stale branches.
- Force pushes without approval.

## 15. Golden Rules
1. One agent = one worktree.
2. One worktree = one branch.
3. One branch = one task.
4. Agents produce code.
5. Orchestrator coordinates.
6. Integrate early and often.
7. Keep main releasable.
