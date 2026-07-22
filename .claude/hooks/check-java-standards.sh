#!/bin/bash
# PostToolUse hook for Edit/Write. Scans the touched file for patterns the
# coding standard bans outright. Exit 2 feeds stderr back to the agent so the
# violation gets fixed in the same turn instead of surfacing in review.

file_path=$(python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get("tool_input", {}).get("file_path", ""))
except Exception:
    pass
')

[[ "$file_path" == *.java && -f "$file_path" ]] || exit 0

# The ArchUnit rules file names the banned patterns in string literals; it is
# the enforcement, not a violation.
[[ "$file_path" == */architecture/ArchitectureRulesTest.java ]] && exit 0

violations=""

check() {
  local pattern="$1" message="$2" hits
  hits=$(grep -nE "$pattern" "$file_path")
  if [[ -n "$hits" ]]; then
    violations+="$message"$'\n'"$hits"$'\n\n'
  fi
}

check '\.block\s*\(' \
  "Blocking call on a reactive type. No .block() anywhere in this codebase (CODING_STANDARD)."

check '\.subscribe\s*\(' \
  "Manual subscribe. Return the Mono/Flux and let the framework subscribe (CODING_STANDARD)."

check '@Autowired' \
  "Field/setter injection. Use constructor injection, preferably @RequiredArgsConstructor (ARCHITECTURE_RULES)."

check 'jakarta\.persistence|javax\.persistence|org\.hibernate' \
  "JPA/Hibernate import. Persistence is Axon/MongoDB on the write side, R2DBC on the read side (TECH_STACK)."

check 'System\.(out|err)\.print|printStackTrace' \
  "Console output. Use SLF4J structured logging (backend-engineer rule)."

if [[ -n "$violations" ]]; then
  echo "Coding standard violations in $file_path:" >&2
  echo "$violations" >&2
  exit 2
fi

exit 0
