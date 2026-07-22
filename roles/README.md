# Roles

Roles are defined as executable subagent definitions in `.claude/agents/`, which is the canonical location; this directory intentionally holds no duplicate charters so the two cannot drift. OpenCode reads the exact same files: `.opencode/opencode.json`'s `agent` block sources each OpenCode agent's `prompt` straight from the matching file in `.claude/agents/` via the `{file:...}` include, so there is one charter per role, not one per tool. `.opencode/agents/AGENTS.md` covers the rules that apply regardless of which agent is active.

Current roles:

- `architect` — designs, aggregate boundaries, event contracts, ADRs. Does not write application code.
- `backend-engineer` — implements tasks from `tasks/` on task branches.
- `test-engineer` — failure-path and edge-case testing, test infrastructure.
- `code-reviewer` — pre-merge review against governance. Read-only.
- `security-reviewer` — security review of auth, payment, logging, and dependency changes. Read-only.

How work flows between roles is defined in `workflow/DEVELOPMENT_WORKFLOW.md`.
