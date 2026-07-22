# Task Index

Keep this table in sync with the task files; the files are authoritative.

| Id | Title | Status | Owner | Depends on |
|---|---|---|---|---|
| [T-001](T-001-test-harness.md) | Establish the integration test harness | done | backend-engineer | — |
| [T-002](T-002-shared-money-and-ids.md) | Shared Money value object and typed identifiers | review | backend-engineer | — |
| [T-003](T-003-payment-aggregate.md) | Payment aggregate with commands and events | backlog | none | T-001, T-002 |
| [T-004](T-004-jwt-security-chain.md) | Reactive JWT security chain | in-progress | opencode | T-001 |
| [T-005](T-005-payment-read-projection.md) | Payment read projection via Kafka to Postgres | backlog | none | T-003 |
