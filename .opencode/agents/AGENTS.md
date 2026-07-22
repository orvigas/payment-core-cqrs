---
disable: true
---

# Agent Instructions

This file exists so that every coding agent (OpenCode, Claude Code, or anything else that reads AGENTS.md) operates under the same rules. It is deliberately thin: the canonical instructions live in `.claude/` and are not duplicated here, so they cannot drift.

The `disable: true` frontmatter above is not documentation content — OpenCode scans `.opencode/agents/` for agent definitions, so without it this file would register as a runnable agent named `AGENTS`. The real developer/tester/reviewer personas (`backend-engineer`, `test-engineer`, `self-review`, `security-review`) are defined in `.opencode/opencode.json`'s `agent` block, each pointing its `prompt` at the matching file under `.claude/agents/` — same charter Claude Code's Task tool uses, no separate copy. `backend-engineer` and the two review subagents also carry a `permission` block in `opencode.json` that mechanically blocks the failure mode a prose-only rule can't: merging, pushing, or checking out `main` from the implementer role, and any file edits at all from either reviewer role.

## Read these before doing anything

1. `.claude/CLAUDE.md` — project state, target stack, commands, testing conventions.
2. `workflow/DEVELOPMENT_WORKFLOW.md` — the task lifecycle. **This is not optional background reading; it is the state machine every task moves through, and its rules are what let multiple agents work in parallel without stepping on each other.** A task file's `status` field only ever holds one of exactly four values: `backlog` → `in-progress` → `review` → `done`. There is no `completed`, no skipping `review`, and no agent grants itself the transition into `review` or `done` — implementers stop at `status: review`; merging to `done` happens only after an independent `code-reviewer` (and `security-reviewer`, for anything touching auth, payment endpoints, logging, config secrets, or dependencies) has actually run, not after you judge your own work sufficient.
3. `.claude/rules/` — mandatory behavior rules. All of them apply to every task:
   - `backend-engineer.md` — engineering role and standards
   - `comment-style.md` — comment and documentation tone; no emojis or decorative elements anywhere
   - `documentation.md` — knowledge entries are written for engineers, not models
   - `git-commits.md` — commit messages must look human-written; no AI attribution of any kind
   - `java-docs.md` — Javadoc requirements, including `@author orvigas@gmail.com` on every top-level type
   - `project-knowledge.md` — capture reusable learnings in `.claude/knowledge/` and index them in `.claude/memory/MEMORY.md`
4. `governance/` — binding engineering law: `ARCHITECTURE_RULES.md`, `CODING_STANDARD.md`, `QUALITY_GATE.md`, `SECURITY_POLICY.md`, `TECH_STACK.md`.

## Non-negotiables (summary, not a substitute for the files above)

- Reactive end to end: no `.block()`, no manual `subscribe()`, no JPA/Hibernate. Kafka listener methods are the only sanctioned blocking spot.
- Constructor injection only. Records for DTOs, sealed types where variants are fixed.
- Events are immutable, versioned records, published only after durable commit; consumers are idempotent.
- Flyway migrations are append-only and cover the read schema only.
- `mvn verify` must pass; JaCoCo enforces a 95% instruction-coverage floor.
- Conventional Commits; never mention AI tooling in commits, comments, or docs.

## Where things live

- Domain model reference: `knowledge/domain/`
- Architecture decisions: `knowledge/decisions/` (ADR format from `.claude/templates/decision-template.md`)
- Reusable engineering knowledge: `.claude/knowledge/`, indexed in `.claude/memory/MEMORY.md`
- Dated change log: `.claude/history/YYYY-MM.md`
