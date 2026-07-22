# Task Ledger

The ledger is the concurrency mechanism for multi-agent work. One task, one owner, one branch; two agents never share a task or a branch. Any agent picking up work must claim it here first.

## Format

One file per task, named `T-NNN-short-slug.md`, created from `.claude/templates/task-template.md`. `TASKS.md` is the index; keep it in sync with the files. Task ids are never reused.

Frontmatter fields:

- `status`: `backlog` | `in-progress` | `review` | `done` | `blocked`
- `owner`: agent or person currently holding the task; `none` when unclaimed
- `branch`: `task/T-NNN-slug` once work starts
- `depends-on`: task ids that must be `done` first

## Lifecycle

```
backlog -> in-progress -> review -> done
              |              |
              v              v
           blocked      in-progress (review found blockers)
```

Rules:

1. Claiming = setting `owner` and `status: in-progress` in one commit on `main` before branching. This is the lock; check the ledger before claiming to avoid collisions.
2. The scope section in the task file lists the only paths the task may touch. Overlapping scopes must not be in progress simultaneously.
3. Moving to `review` requires a green `mvn verify` on the task branch and a handoff-log entry.
4. `done` requires the review verdict and the branch merged to `main`.
5. `blocked` always names the blocking task or question in the handoff log.

Full workflow, including which role does what at each transition, lives in `workflow/DEVELOPMENT_WORKFLOW.md`.
