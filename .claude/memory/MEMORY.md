# Knowledge Index

- [Spring Boot 4 Upgrade](../knowledge/spring-boot-4-upgrade.md) — coordinated dependency bumps and silent-failure gotchas when moving from Boot 3.5 to 4.1
- [Testcontainers Forked-JVM Isolation](../knowledge/testcontainers-forked-jvm-isolation.md) — why forking a child JVM to test "Docker unavailable" doesn't isolate DOCKER_HOST by default
- [Money vs Settlement Negative Amount Gap](../knowledge/money-settlement-negative-amount-gap.md) — Money's non-negative invariant can't represent Settlement's netAmount, which is legitimately signed; unresolved, needs a decision when Settlement is scoped
- [Resilience4j RateLimiterAspect Missing on Spring Boot 4](../knowledge/resilience4j-ratelimiter-aspect-spring-boot-4.md) — `@RateLimiter` is silently ignored on WebFlux unless the aspect is registered manually because Resilience4j 2.4.0 requires RxJava 3 for the main aspect bean
- [ADR-001 Axon + MongoDB event store](../../knowledge/decisions/adr-001-axon-mongodb-event-store.md) — why the write side uses Axon over MongoDB while Postgres holds only read projections
