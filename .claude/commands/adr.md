---
description: Record an architecture decision as the next numbered ADR
argument-hint: short decision title
---

Record an architecture decision: $ARGUMENTS

1. Find the highest ADR number in `knowledge/decisions/` and use the next one.
2. Create `knowledge/decisions/adr-NNN-short-slug.md` from `.claude/templates/decision-template.md`.
3. Write real content per the documentation rule: the context that forced the decision, the options actually considered, why the winner won, and the consequences including follow-up work. Name the rejected alternatives — that is the part future readers need most.
4. Add a pointer line to `.claude/memory/MEMORY.md` and, if the decision is notable, a dated entry in `.claude/history/YYYY-MM.md`.
5. Commit with a `docs:` message.

If the decision context is unclear, ask what alternatives were considered before writing; an ADR without real alternatives is not worth recording.
