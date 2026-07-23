---
id: T-006
title: Configure codegraph MCP server for OpenCode
status: done
owner: opencode
branch: chore/T-006-opencode-codegraph-mcp
depends-on: []
---

# T-006: Configure codegraph MCP server for OpenCode

## Goal

OpenCode agents have access to the same codegraph code-intelligence tools (symbol search, callers/callees, impact analysis) that Claude Code already gets from the global MCP config, so an OpenCode session can look up code structure without falling back to plain grep.

## Scope

- `.opencode/opencode.json`

## Acceptance criteria

- [x] `.opencode/opencode.json` declares a `mcp.codegraph` entry (`type: local`, `command: ["codegraph", "serve", "--mcp"]`) matching the server already registered globally for Claude Code in `~/.claude.json`.
- [x] `opencode mcp list` shows `codegraph` as connected.
- [x] `opencode debug config` resolves the `mcp.codegraph` block from this file.
- [x] A live `opencode run` successfully invokes a codegraph tool (`codegraph_codegraph_status`) and returns real index data.

## Notes

The project's `.codegraph/` index already existed (index daemon running, `codegraph.db` populated), so this task is config wiring only — no `codegraph init`/`index` step was needed.

## Handoff log

- 2026-07-22 (opencode): Added `mcp.codegraph` block to `.opencode/opencode.json`. Verified via `opencode mcp list` (connected), `opencode debug config` (resolved), and a live `opencode run` that called `codegraph_codegraph_status` and returned a healthy index summary (61 files, 786 nodes, 1,084 edges). Task opened and closed in the same change since implementation and verification were already complete.
