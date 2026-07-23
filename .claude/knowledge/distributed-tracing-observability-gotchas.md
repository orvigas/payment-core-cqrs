# Spring Boot 4.1 Observability: Metrics, Tracing, Logging Gotchas

## MeterRegistryCustomizer package moved

In Spring Boot 4.1, `MeterRegistryCustomizer` moved from `org.springframework.boot.actuate.autoconfigure.metrics` to `org.springframework.boot.micrometer.metrics.autoconfigure`. The old package does not exist in the new `spring-boot-micrometer-metrics` module.

## Micrometer API changes in 1.17

- `Timer.Builder.publishPercentileHistogram()` — the plural form `publishPercentileHistograms()` was removed; use singular.
- `MeterRegistry.Config.commonTags()` returns `Config` (fluent setter), not a `List<Tag>`. There is no getter to inspect common tags. Use `meter.getId().getTags()` on a registered meter to verify common tags were applied.
- `MeterRegistryCustomizer` is a `@FunctionalInterface` with a single `customize(MeterRegistry)` method, matching the pre-Boot-4 API.

## loki-logback-appender 2.0.3 XML configuration

Version 2.0.3 (the version managed by the Boot 4.1 BOM) changed the XML schema significantly from the 1.x line:

- `<http>` no longer takes a `class` attribute for the sender. It is a nested element with `<url>` and `<sender class="...">` sub-elements.
- The sender class is `com.github.loki4j.logback.JavaHttpSender` (uses Java 11+ built-in HTTP client). The old `com.github.loki4j.logback.HttpURLConnectionSender` no longer exists.
- The `<format>` tag is gone. Use `<labels>` for the label pattern and `<message>` for the message layout.
- `<writeBuffer>` does not exist. Use `<batch>` with `<maxItems>`, `<maxBytes>`, `<timeoutMs>` etc.
- A failing Loki appender (bad URL, wrong class, unreachable host) causes Logback configuration errors that prevent the Spring context from loading at all — it does not degrade gracefully.

## Reactor context propagation

`Hooks.enableAutomaticContextPropagation()` must be called before any reactive operator chains are created. The safest place is a static initializer in `main()` and also in a `@PostConstruct` on a `@Configuration` class.

The `@PostConstruct` method is needed because in a `@SpringBootTest` context, the `main()` method is not called — only the application context is bootstrapped. Without the `@PostConstruct`, tests that exercise reactive chains would lose trace context.

When using `Hooks.enableAutomaticContextPropagation()`, the `Tracing` bean from `micrometer-tracing-bridge-brave` is not required to exist at configuration time. The hook works at the Reactor level, propagating whatever context is present when operators run.

## logback-spring.xml integration test impact

`logback-spring.xml` (as opposed to `logback-test.xml`) is loaded by every Spring Boot application context, including test contexts. An error in the logback config (missing class, wrong schema) prevents the entire Spring context from loading, not just logging.

Workaround: use Profile-specific logback config or ensure the logback config tolerates unreachable endpoints.

## keywords

spring boot 4, micrometer, metrics, tracing, prometheus, brave, loki, logback, reactor context propagation, Hooks.enableAutomaticContextPropagation

Related: [[spring-boot-4-upgrade]]
