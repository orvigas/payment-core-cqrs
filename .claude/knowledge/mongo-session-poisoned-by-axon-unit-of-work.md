# Auxiliary MongoTemplate Writes Fail with NoSuchTransaction Inside an Axon Command Handler

## What was the problem?

An `@CommandHandler`-annotated Spring component (`PaymentCommandHandler`, not the aggregate
itself) did a `mongoTemplate.insert(...)` to atomically reserve an idempotency key, and on a
duplicate key caught the resulting `DuplicateKeyException` and fell back to a plain
`mongoTemplate.findOne(...)` to look up the winner. Integration tests hit that fallback path
(duplicate idempotency key, or a second write attempt after a failed one) and got a 500 instead
of the expected response. The server log showed:

```
com.mongodb.MongoQueryException: Command execution failed on MongoDB server with error 251
(NoSuchTransaction): 'Transaction with { txnNumber: N } has been aborted.'
```

The insert itself (the very first Mongo operation) always succeeded or failed as expected; it
was specifically the *next* Mongo operation on the same request thread, after a failed one, that
blew up.

## What was the root cause?

Axon's MongoDB event store needs multi-document transactions against a replica set (the token
store and event append both write multiple documents atomically), so the `DefaultUnitOfWork`
that wraps command handling has a MongoDB session/transaction bound to the handling thread.

Spring Data MongoDB's `MongoTemplate` defaults to `SessionSynchronization.ON_ACTUAL_TRANSACTION`:
if a Spring-managed transaction is already active on the thread, *any* `MongoTemplate` operation
- even one with no `@Transactional` annotation of its own, on an entirely unrelated collection -
silently joins it. That's true for the shared, Spring Boot-autoconfigured `MongoTemplate` bean
that both Axon's infrastructure and this application code were injecting.

MongoDB aborts an entire multi-document transaction on the first operation that fails inside it.
Once the idempotency insert failed with a duplicate-key error, the whole session was dead; the
fallback read that ran immediately after, in the same UnitOfWork, inherited that dead session and
failed with `NoSuchTransaction` - even though it has nothing to do with event sourcing.

## How was it diagnosed?

1. Isolated the failing test (`mvn test -Dtest=ClassName#method`) and read the full stack trace
   rather than the assertion failure alone - the assertion just said "expected 200, got 500".
2. The trace showed the *read* failing, at `MongoTemplate.findOne`, called from inside
   `PaymentCommandHandler.handle`, itself called from `SimpleCommandBus` / `DefaultUnitOfWork`.
3. The error code (251, `NoSuchTransaction`, "has been aborted") is MongoDB-specific for "a prior
   operation in this transaction failed and the whole transaction is now dead" - that pointed at
   session/transaction sharing rather than a query bug.
4. Confirmed the first write alone (no code path after it) always succeeded, isolating the
   trigger to "a second Mongo op after a failed one, same thread."

## What was the fix?

Give the auxiliary repository its own `MongoTemplate`, built from the shared
`MongoDatabaseFactory` bean but with session synchronization turned off, instead of injecting the
Spring Boot-managed `MongoTemplate`:

```java
public MongoPaymentIdempotencyRepository(MongoDatabaseFactory mongoDatabaseFactory) {
    this.mongoTemplate = new MongoTemplate(mongoDatabaseFactory);
    this.mongoTemplate.setSessionSynchronization(SessionSynchronization.NEVER);
}
```

`MongoDatabaseFactory` is still the one Spring Boot autoconfigures (same `MongoClient`, same
driver-level settings like the UUID representation customizer), so this doesn't lose any
connection-level configuration - it only stops this specific `MongoTemplate` instance from
opportunistically joining someone else's transaction.

## Why does the solution work?

`SessionSynchronization.NEVER` tells Spring Data MongoDB never to look up a thread-bound session
via `TransactionSynchronizationManager`, so every operation through this `MongoTemplate` runs as
an ordinary standalone command against its own implicit session, regardless of what the calling
thread happens to have open. A failure in this collection can no longer poison, or be poisoned
by, whatever transaction Axon's event store has going.

## What are the trade-offs or limitations?

- This repository's writes are no longer transactionally consistent with the aggregate's event
  append in the rare case both need to succeed together (they don't here - the idempotency row
  and the aggregate's events are intentionally allowed to diverge slightly, same as before this
  fix; see the "Known issues" style caveat in `PaymentCommandHandler`'s Javadoc history).
- Don't reach for `SessionSynchronization.NEVER` on the *shared* `MongoTemplate` bean - that would
  silently break any code that actually relies on joining an active transaction. Scope it to a
  private `MongoTemplate` instance owned by the class that needs it, as done here.

## How can this issue be prevented?

- Whenever application code does a `MongoTemplate`/`MongoRepository` write from inside code that
  executes as part of Axon command handling (whether the aggregate itself or an application-level
  `@CommandHandler`), assume it may be running inside Axon's transactional session, and test the
  actual failure path (not just the happy path) with a real Testcontainers Mongo - the happy path
  alone won't surface this.
- A quick smoke test: deliberately trigger a duplicate-key or constraint failure on the auxiliary
  collection, immediately followed by another read/write to the same collection, inside a real
  command dispatch. If that second operation throws `NoSuchTransaction`, this is the same issue.

## Which versions, libraries, or environments are affected?

- Spring Data MongoDB 5.1.0 / Spring Boot 4.1.0
- Axon Framework 4.10.3 with `axon-mongo` extension, replica-set event store
  (`axon.mongo.event-store.enabled=true`, `token-store.enabled=true`)
- Any MongoDB driver version using multi-document transactions (server 4.0+, replica set)

## Are there related issues or documentation?

- `src/main/java/com/orvigas/payment/idempotency/MongoPaymentIdempotencyRepository.java`
- `src/main/java/com/orvigas/payment/PaymentCommandHandler.java`
- `src/test/java/com/orvigas/support/AbstractIntegrationTest.java` (explains why the Mongo
  Testcontainers instance runs as a single-node replica set - the same transaction requirement)
- `tasks/T-008-payment-rest-api.md` handoff log, 2026-07-22 review-fixes entry

## What keywords would help someone find this entry later?

MongoDB, NoSuchTransaction, transaction aborted, SessionSynchronization, ON_ACTUAL_TRANSACTION,
MongoTemplate, Axon UnitOfWork, DefaultUnitOfWork, replica set transaction, DuplicateKeyException,
idempotency, command handler, TransactionSynchronizationManager, session poisoning
