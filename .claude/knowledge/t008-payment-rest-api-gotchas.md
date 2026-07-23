# T-008: Payment REST API Implementation Gotchas

## MongoDB UUID representation

Axon's MongoDB event store requires UUIDs stored in a canonical binary representation (standard Java UUID encoding), but Spring Boot 4.1 / MongoDB driver 5.x defaults to `Binary` subtype `0x04` (UUID legacy). The mismatch produces "org.bson.BsonSerializationException: Not enough bytes to read UUID" at runtime.

**Fix:** Register a `MongoClientSettingsBuilderCustomizer` bean that sets `uuidRepresentation(UuidRepresentation.JAVA_LEGACY)` and `uuidRepresentation(UuidRepresentation.STANDARD)`. The property path `spring.data.mongodb.uuid-representation` that worked in Boot 3.x is broken in 4.1 — it never reaches the client settings builder.

## Jackson auto-detects `is*()` boolean methods

`Money.isZero()` and `Money.isPositive()` are Jackson-visible by default since they follow the `is*()` JavaBean convention. Jackson serializes them as JSON properties alongside `minorUnits`/`currency`, producing unexpected fields in API responses.

**Fix:** Add `@JsonIgnore` on `isZero()` and `isPositive()` in `Money.java`.

## `@Transactional` conflicts in reactive context

Spring Boot 4.1 auto-configures both a reactive `ReactiveTransactionManager` (for R2DBC) and a platform `TransactionManager` (for the MongoDB driver's blocking paths). A `@Transactional` annotation on a reactive `@Service` method causes a bean-ambiguity error at startup: "No qualifying bean of type 'TransactionManager'".

**Fix:** Never use `@Transactional` on reactive methods in this codebase. If a reactive transaction boundary is needed, use the `TransactionalOperator` API explicitly.

## Void-returning Axon command handlers

When the `CommandGateway` is used non-blockingly (`.send()` returns `CompletableFuture`), wrapping a void-returning handler in a reactive chain requires `.then(Mono.just(response))` rather than `.map(response -> ...)`, because `.send()` produces a void `CompletableFuture<Void>` and `.map()` never executes.

**Fix:** Structure reactive chains as `Mono.fromFuture(gateway.send(cmd)).then(Mono.just(result))`.

## Keywords

T-008, Payment REST API, MongoDB UUID, UUID representation, Jackson `is*()`, Money serialization, `@Transactional` ambiguity, reactive void handler, Axon CommandGateway
