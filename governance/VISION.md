# Vision

## Why this platform exists

Payment Core is the system of record for the payment lifecycle: initiation, charging, completion, and failure handling. Money movement demands a level of auditability and correctness that CRUD-over-a-mutable-row cannot provide, which is why the platform is built on DDD, CQRS, Event Sourcing, and Sagas rather than a conventional layered monolith.

## Where we are going

Over the next iterations the platform should converge on the following end state:

- **Every state change is an event.** The event log is the source of truth; read models are disposable projections that can be rebuilt from it at any time. Losing a projection is an inconvenience; losing the log is unacceptable.
- **Commands and queries never share a model.** Write-side aggregates enforce invariants; read-side views are shaped by what consumers actually need. Neither is contorted to serve the other.
- **Long-running flows are Sagas, not distributed transactions.** Cross-aggregate consistency is achieved through compensating actions, never through two-phase commit or synchronous fan-out.
- **Fully reactive request path.** WebFlux and R2DBC end to end, with Kafka consumer threads as the one deliberate blocking boundary. Throughput scales with connections, not threads.
- **Operable by default.** Every feature ships with metrics, traces, and structured logs. An on-call engineer diagnosing an incident at 3 a.m. should never need to add instrumentation first.

## What we optimize for, in order

1. **Correctness** — a payment is charged exactly once, or provably not at all. No optimization justifies weakening this.
2. **Auditability** — the full history of every payment is reconstructable from the event log.
3. **Maintainability** — a new engineer should be able to locate the code for any business rule within minutes. Architectural consistency beats local cleverness.
4. **Scalability** — horizontal scaling through partitioned topics and stateless services, not bigger boxes.
5. **Delivery speed** — last, and only within the constraints above. We go fast by keeping quality gates automated, not by skipping them.

## What we deliberately do not do

- No shared database between services. Integration happens through events.
- No synchronous service-to-service call chains for business flows. If a flow needs multiple services, it is a Saga.
- No framework migrations chasing novelty. The stack in `TECH_STACK.md` changes when there is a concrete, documented reason, not because a newer option exists.
- No feature work that bypasses the quality gate (`QUALITY_GATE.md`). A feature that cannot pass the gate is not done.

## How to use this document

When two designs both satisfy a requirement, pick the one that better serves the priorities above, in order. When a proposal conflicts with this vision, either the proposal changes or this document does — silently diverging is not an option. Changes to this file go through the same review as code.
