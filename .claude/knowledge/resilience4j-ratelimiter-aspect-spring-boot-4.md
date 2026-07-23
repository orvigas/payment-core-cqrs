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

## Addendum: per-caller (dynamic) instance names via SpEL

`@RateLimiter(name = "...")` supports a SpEL expression instead of a fixed instance name, which
is how to get a separate bucket per caller (e.g. per merchant id) instead of one global bucket
shared by everyone - useful when a single static name like `login`'s isn't fine-grained enough
(T-008's capture/refund/initiate endpoints needed exactly this).

The catch is syntax: `DefaultSpelResolver.resolve` checks the raw annotation string against three
patterns, in order, before evaluating anything:

1. `^[$#]\{.+}$` (i.e. `${...}` or `#{...}`) → resolved via Spring's embedded value
   resolver/`BeanExpressionResolver`, which evaluates against a `BeanExpressionContext` - **no
   access to method arguments**. `#callerMerchantId` inside a `#{...}` block silently fails to
   resolve as a method parameter here.
2. `^#.+$` (starts with a bare `#`, not wrapped in braces) → evaluated as SpEL against a
   `MethodBasedEvaluationContext`, which *does* expose method parameters by name (`#paramName`),
   the same mechanism `@Cacheable(key = ...)` uses.
3. `^@.+$` → bean reference.
4. Anything else (including an expression that's syntactically valid SpEL but doesn't start with
   `#`, like `'foo-' + #bar`) is treated as a **literal string name**, not evaluated at all.

So to reference a method parameter, the expression must literally start with `#` - put the
variable reference first, not a string literal:

```java
// Wrong: looks reasonable, but PLACEHOLDER_SPEL_REGEX intercepts it first (BeanExpressionContext,
// no method args) - either fails to resolve #callerMerchantId or is a plain unresolved literal.
@RateLimiter(name = "#{'payment-' + #callerMerchantId.value()}")

// Also wrong: doesn't start with '#', so it's never parsed as SpEL at all - the rate limiter's
// instance name is literally the 33-character string "'payment-' + #callerMerchantId.value()".
@RateLimiter(name = "'payment-' + #callerMerchantId.value()")

// Right: starts with '#', matches the METHOD_SPEL_REGEX branch, evaluates with method args bound.
@RateLimiter(name = "#callerMerchantId.value() + '-payment'")
```

Dynamically-named instances that aren't preconfigured under
`resilience4j.ratelimiter.instances.<name>` fall back to `resilience4j.ratelimiter.configs.default`
(or Resilience4j's hardcoded default if that isn't set either) - set `configs.default` explicitly
rather than relying on the library default if the limit matters. In tests, override
`resilience4j.ratelimiter.configs.default.limit-for-period` via `@DynamicPropertySource`, since
the per-caller instance name isn't known ahead of time and can't be targeted by
`resilience4j.ratelimiter.instances.<name>` the way a fixed name like `login` can.

Keywords: resilience4j SpEL, RateLimiter name expression, per-user rate limit, dynamic rate
limiter instance, MethodBasedEvaluationContext, DefaultSpelResolver, configs.default
