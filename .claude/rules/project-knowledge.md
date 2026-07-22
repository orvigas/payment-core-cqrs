# Project Knowledge Rule (MANDATORY)

Before finishing any task, ask: "Did I learn something that will save future work?"

If yes, update the knowledge base. If no, do nothing.

## Responsibility

Whenever an error is solved, a bug is fixed, a dependency issue appears, an architectural decision is made, a performance improvement is discovered, or a framework limitation is identified, determine whether the knowledge is reusable.

If reusable:

1. Search `.claude/knowledge/` for an existing file on the topic before writing a new one.
2. Update the existing file instead of creating a duplicate.
3. Keep entries concise and cross-link related ones with `[[name]]`.
4. Add or update a one-line pointer in `.claude/memory/MEMORY.md`.
5. For a fix or decision worth a repeatable structure, base it on `.claude/templates/`.

## Directory Structure

```text
.claude/
├── CLAUDE.md
├── rules/                  # Mandatory behavior rules (this directory)
├── knowledge/              # Topic-based reusable knowledge (architecture decisions, migrations, gotchas)
├── memory/
│   └── MEMORY.md           # Index of every knowledge entry
├── history/                # Dated log of notable changes and decisions (YYYY-MM.md)
└── templates/
    ├── fix-template.md
    └── decision-template.md
```

## When to Log to History

Append a dated entry to `.claude/history/YYYY-MM.md` for notable fixes, decisions, or incidents that don't warrant a standalone knowledge file but are worth tracing later (for example, "why we bumped Spring Kafka to 4.1.0").
