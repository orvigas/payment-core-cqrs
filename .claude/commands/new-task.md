---
description: Create a new task in the ledger from the task template
argument-hint: short task title
---

Create a new task in the ledger for: $ARGUMENTS

1. Find the highest existing task id in `tasks/` and use the next number (ids are never reused).
2. Create `tasks/T-NNN-short-slug.md` from `.claude/templates/task-template.md`. Fill in a real goal, a concrete scope (paths only this task will touch — check in-progress tasks for overlap), and checkable acceptance criteria referencing the relevant domain docs and quality gates.
3. Add the row to `tasks/TASKS.md`.
4. Commit both files to `main` with a `chore(tasks):` message.

If the request is too vague to write real acceptance criteria, ask for the missing specifics instead of writing placeholders.
