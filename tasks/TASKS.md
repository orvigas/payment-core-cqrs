# Task Index

Keep this table in sync with the task files; the files are authoritative.

| Id | Title | Status | Owner | Depends on |
|---|---|---|---|---|
| [T-001](T-001-test-harness.md) | Establish the integration test harness | done | backend-engineer | — |
| [T-002](T-002-shared-money-and-ids.md) | Shared Money value object and typed identifiers | done | backend-engineer | — |
| [T-003](T-003-payment-aggregate.md) | Payment aggregate with commands and events | done | backend-engineer | T-001, T-002 |
| [T-004](T-004-jwt-security-chain.md) | Reactive JWT security chain | done | opencode | T-001 |
| [T-005](T-005-payment-read-projection.md) | Payment read projection via Kafka to Postgres | backlog | none | T-003, T-007 |
| [T-006](T-006-opencode-codegraph-mcp.md) | Configure codegraph MCP server for OpenCode | done | opencode | — |
| [T-007](T-007-payment-kafka-publisher.md) | Kafka event publisher for payment domain events | in-progress | backend-engineer | T-003 |
| [T-008](T-008-payment-rest-api.md) | Payment REST API | review | general | T-003, T-004 |
| [T-009](T-009-merchant-aggregate.md) | Merchant aggregate with onboarding and lifecycle commands | review | general | T-001, T-002 |
| [T-010](T-010-merchant-status-projection.md) | Merchant status projection and payment-side enforcement | backlog | none | T-009, T-007 |
| [T-011](T-011-settlement-aggregate-payout-saga.md) | Settlement aggregate and payout saga | backlog | none | T-007, T-009 |
| [T-012](T-012-observability-wiring.md) | Observability wiring (metrics, tracing, structured logs) | review | general | — |
| [T-013](T-013-k6-load-tests.md) | K6 load test scenarios for the payment API | backlog | none | T-008 |
