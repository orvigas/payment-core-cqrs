# Resilience4j RateLimiterAspect Missing on Spring Boot 4 Without RxJava 3

## What was the problem?

The `@RateLimiter` annotation on a WebFlux `Mono`-returning method was silently ignored. Requests were not throttled, the rate limiter metrics stayed at zero, and integration tests that expected a `429` received `200` instead. There was no startup error or log warning.

## What was the root cause?

Resilience4j 2.4.0's `resilience4j-spring-boot4` auto-configuration has a bug in the `RateLimiterAspect` bean registration:

- The main `RateLimiterAspect` bean is created only when `RxJava3OnClasspathCondition` and `AspectJOnClasspathCondition` are both true.
- The `ReactorRateLimiterAspectExt` bean is created correctly when Reactor and AspectJ are present.
- In a WebFlux-only project, Reactor and AspectJ are present but RxJava 3 is not. The reactor extension is created, but the main aspect is never registered, so the `@RateLimiter` pointcut is never applied.

The `RateLimiterRefreshScopedRegistryAutoConfiguration` is also conditional on Spring Cloud `RefreshScope`, so the refresh-scoped registry path does not help without Spring Cloud. The non-refresh registry still works because `RateLimiterAutoConfiguration` binds `RateLimiterProperties` directly.

## How was it diagnosed?

1. Confirmed the `LoginController` bean was a CGLIB proxy and that `ReactorRateLimiterAspectExt` existed in the context.
2. Confirmed the `RateLimiterRegistry` had the expected `login` instance with the correct limit.
3. Decompiled `RateLimiterAutoConfiguration` and saw that `rateLimiterAspect()` carries `@Conditional(RxJava3OnClasspathCondition.class, AspectJOnClasspathCondition.class)` instead of the expected Reactor/AspectJ condition.
4. Verified that the third request in a login test was not blocked even though the limit was set to 2.

## What was the solution?

Register the `RateLimiterAspect` manually as a fallback bean:

```java
@Configuration
public class RateLimiterAspectConfig {

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterAspect rateLimiterAspect(
            RateLimiterRegistry rateLimiterRegistry,
            RateLimiterConfigurationProperties rateLimiterConfigurationProperties,
            List<RateLimiterAspectExt> rateLimiterAspectExtList,
            FallbackExecutor fallbackExecutor,
            SpelResolver spelResolver) {
        return new RateLimiterAspect(
                rateLimiterRegistry,
                rateLimiterConfigurationProperties,
                rateLimiterAspectExtList,
                fallbackExecutor,
                spelResolver);
    }
}
```

The `@ConditionalOnMissingBean` ensures the manual bean disappears if the upstream auto-configuration is ever fixed. The manual bean reuses the already-created `ReactorRateLimiterAspectExt`, so `Mono` and `Flux` methods are decorated correctly.

## Why does the solution work?

The `RateLimiterAspect` class is the actual `@Aspect` that contains the pointcut. Once it is a bean, Spring AOP applies it to any method annotated with `@RateLimiter`. The aspect delegates to the injected extensions, and the reactor extension knows how to wrap a `Mono` with `RateLimiterOperator`. The auto-configured registry and configuration are still used, so the behavior matches the declared `resilience4j.ratelimiter` properties.

## What are the trade-offs or limitations?

- This is a workaround for a library bug. It should be removed when Resilience4j fixes the condition.
- The bean is only created if the auto-configuration does not provide it. This avoids duplication but couples the project to the current Resilience4j internals.
- No Spring Cloud dependency is needed.

## How can this issue be prevented?

- Always verify rate limiting with an integration test that actually exceeds the limit.
- Check the auto-config report or the bean context for `RateLimiterAspect` when rate limits are silently ignored.
- Keep `resilience4j-reactor` and `spring-boot-starter-aspectj` on the classpath.

## Which versions, libraries, or environments are affected?

- `io.github.resilience4j:resilience4j-spring-boot4:2.4.0`
- Spring Boot 4.1.x
- WebFlux / Reactor stack without RxJava 3

## Are there related issues or documentation?

- `tasks/T-004-jwt-security-chain.md`
- `src/main/java/com/orvigas/security/config/RateLimiterAspectConfig.java`

## What keywords would help someone find this entry later?

resilience4j, ratelimiter, aspect, spring-boot4, webflux, reactor, rxjava3, silent failure, rate limiting, RateLimiterAspect, ReactorRateLimiterAspectExt
