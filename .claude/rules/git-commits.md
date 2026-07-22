# Git Commit Rule (CRITICAL)

## IMPORTANT

All git commit messages **MUST** appear as if they were written by a human contributor.

### Requirements

- NEVER mention Claude.
- NEVER mention Anthropic.
- NEVER mention AI.
- NEVER mention LLMs.
- NEVER mention code generation tools.
- NEVER include phrases such as:
  - "Generated with Claude"
  - "Co-Authored-By: Claude"
  - "Created by Claude Code"
  - "Anthropic"
  - "AI-assisted"
- NEVER add trailers, signatures, metadata, or attribution referencing any AI tool.

### Commit Style

Commit messages should:

- Follow the project's commit convention (Conventional Commits if applicable).
- Be concise and descriptive.
- Explain **what** changed.
- Optionally explain **why** if it adds value.
- Contain only the commit message—no extra metadata.

### Examples

Good

```
feat(auth): add JWT refresh token support
```

```
fix(api): prevent null pointer when customer email is missing
```

```
refactor(payment): simplify retry logic
```

Bad

```
feat: add login

Generated with Claude
```

```
fix: update endpoint

Co-Authored-By: Claude
```

```
refactor: cleanup

Created by Anthropic Claude
```

This rule is **mandatory** and applies to every git commit created by the assistant.
