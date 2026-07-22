# Spring Boot 3.5 to 4.1 Upgrade Notes

## What changed

In July 2026 the governance docs were updated to target Spring Boot 4.1.0 (GA June 10, 2026) instead of 3.5.16. Boot 4 modularized its autoconfiguration and moved to Spring Framework 7 / Spring Security 7, which breaks several dependencies that worked untouched on Boot 3. These are the coordinated bumps required, verified against release announcements and upstream issue trackers.

## Required companion changes

- **Spring Kafka 4.1.0** (GA June 9, 2026, released in lockstep with Boot 4.1.0). Boot 4 requires the new `spring-boot-starter-kafka` starter; declaring only `spring-kafka` compiles but the Kafka autoconfiguration never loads (spring-kafka issue 4278). `spring-kafka-test` moves to the same version.
- **SpringDoc 3.0.3.** The 2.8.x line targets Boot 3 and fails on Boot 4 because `WebFluxProperties` moved from `org.springframework.boot.autoconfigure.web.reactive` to `org.springframework.boot.webflux.autoconfigure` (springdoc issue 3196). Only the 3.x line supports Boot 4. Still `springdoc-openapi-starter-webflux-ui`, never the WebMVC artifact.
- **Resilience4j: swap `resilience4j-spring-boot3` for `resilience4j-spring-boot4`**, available since 2.4.0. The new artifact was initially omitted from the resilience4j BOM, so declare its version explicitly rather than relying on the BOM (resilience4j issues 2351, 2427).
- **Micrometer artifacts follow the Boot BOM.** The old explicit pins (`micrometer-registry-prometheus` 1.15.12, `micrometer-tracing-bridge-brave` 1.5.12) matched Boot 3.5's managed versions; re-pinning them under Boot 4.1 would downgrade what the BOM manages. TECH_STACK.md now marks them "via Boot BOM".

## Why 4.1.0 and not 4.0.x

4.1.0 is the latest stable line (active support to July 2027); 4.0.x support ends December 2026. New projects should target 4.1.

## How to avoid regressions

When bumping the Boot parent in the future, check every dependency whose artifact name encodes the Boot generation (`resilience4j-spring-boot4`, springdoc major line) and any starter the modularized autoconfiguration split out — a missing starter fails silently at runtime, not at compile time.

## Keywords

spring boot 4, upgrade, migration, spring kafka starter, springdoc 3, resilience4j-spring-boot4, WebFluxProperties, boot bom

Related: [[r2dbc-migration-gotchas]], [[distributed-tracing-observability-gotchas]]
