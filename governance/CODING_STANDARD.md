# Coding Standard

Binding rules for all production and test code in this repository. `ARCHITECTURE_RULES.md` governs design; this document governs how the code itself is written. Where the two overlap, the stricter rule wins.

## 1. Language Level

- Java 21, compiled with `--release 21`. No preview features in `main`.
- **Records** for all DTOs, API request/response types, events, and value objects. A class with mutable fields is only acceptable when a framework demands it (e.g., `@ConfigurationProperties` binding), and then it stays package-private where possible.
- **Sealed interfaces/classes** when the set of variants is closed and known (e.g., payment states, saga steps). Pair them with exhaustive `switch` expressions so adding a variant fails compilation everywhere it matters, instead of failing at runtime.
- **Pattern matching** (`instanceof` patterns, record patterns, switch patterns) over cast-and-check chains.
- `var` only when the type is obvious from the right-hand side (`var payment = new Payment(...)`). Never for method-call results whose type a reader would have to look up.
- Text blocks for multi-line strings (SQL, JSON fixtures); no string concatenation across lines.

## 2. Nullability and Optionals

- `Optional` for return values only — never for fields, parameters, or collection elements.
- Never return `null` from a public method; return `Optional.empty()`, an empty collection, or a `Mono.empty()`/`Flux.empty()`.
- Validate constructor and method arguments eagerly (`Objects.requireNonNull`, Bean Validation) so a bad value fails at the boundary, not three layers down.

## 3. Reactive Code (WebFlux / Reactor)

- Return `Mono`/`Flux` from every method on the request path; never `.block()`, `.toFuture().get()`, or `Thread.sleep()` outside test code and Kafka listener threads.
- Never start work by subscribing manually inside application code — return the publisher and let the framework subscribe. A `subscribe()` call in a service is a bug unless it is an intentional fire-and-forget with error handling attached.
- Side effects belong in `doOnNext`/`doOnError`/`doFinally`, not buried in `map` lambdas.
- Propagate context (auth, trace IDs) through the Reactor `Context`, never through thread-locals.
- Prefer composition (`flatMap`, `zip`, `switchIfEmpty`) over nested subscriptions. A `flatMap` inside a `flatMap` inside a `flatMap` is a signal to extract a named private method.
- Every external call gets a timeout; every retry uses `Retry.backoff` with jitter and a bounded attempt count.

## 4. Errors and Exceptions

- Custom exceptions extend a small, sealed domain hierarchy; never throw or catch bare `Exception`/`RuntimeException`.
- Catch an exception only to add context, translate it at a boundary, or genuinely recover. Log-and-rethrow at multiple levels produces duplicate noise — log where the exception is finally handled.
- All API error responses go through the single global handler and follow RFC 7807 Problem Details. Controllers never build error bodies by hand.
- No error swallowing: an empty catch block, a `.onErrorResume(e -> Mono.empty())` without justification, or a logged-then-ignored failure in a money path is a review blocker.

## 5. Naming and Structure

- Packages by feature (`payment`, `charge`, `notification`), not by layer (`controllers`, `services` at top level). Layers live inside the feature package.
- Class names state role: `PaymentCommandHandler`, `PaymentProjection`, `ChargeSaga`. No `Manager`, `Helper`, `Util` grab-bags; a "utility" method belongs on the type whose data it operates on.
- Methods are verbs, booleans read as predicates (`isSettled`, `hasExpired`), and a method longer than roughly 30 lines or with more than 3 levels of nesting gets decomposed.
- Constants over magic numbers/strings — including Kafka topic names, header names, and claim keys, which live in one place each.

## 6. Spring Conventions

- Constructor injection only, via Lombok `@RequiredArgsConstructor` with `final` fields. `@Autowired` on fields is banned.
- Grouped configuration through typed `@ConfigurationProperties` records; `@Value` only for a genuinely standalone property.
- Beans are stateless. Any mutable state in a singleton bean must be justified in a comment and thread-safe.
- Slice tests use the matching slice annotation (`@WebFluxTest`, `@DataR2dbcTest`); `@SpringBootTest` is reserved for genuine end-to-end wiring tests.

## 7. Lombok

Allowed: `@RequiredArgsConstructor`, `@Slf4j`, `@Builder` (on aggregates where records don't fit), `@Getter` on entity classes. Not allowed: `@Data` on entities (its generated `equals`/`hashCode` breaks on persistent identity), `@SneakyThrows`, `@Synchronized`, and `val`.

## 8. Comments and Documentation

- Follow `.claude/rules/comment-style.md` and `.claude/rules/java-docs.md`: Javadoc on every public type and member, comments explain why rather than what, no decorative elements.
- A `TODO` must carry an issue reference (`TODO(#123): ...`); orphan TODOs are removed at review.

## 9. Formatting and Static Analysis

- Formatting is machine-enforced (Spotless with the Google Java Format profile, 4-space indent override); no manual style debates in review. Unformatted code fails the build.
- The static-analysis toolchain in `QUALITY_GATE.md` (Checkstyle, SpotBugs/ErrorProne, Sonar rules) runs in CI; suppressions require an inline justification comment and reviewer sign-off.
- Warnings are errors: `@SuppressWarnings` needs a one-line reason, and raw types are never suppressed.

## 10. Version Control

- Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`, `ci:`), imperative mood, scoped to the feature (`feat(payment): ...`).
- Small, reviewable changes: one logical change per commit, one concern per PR. A PR mixing a refactor with a behavior change gets split.
- Migrations, generated code, and formatting-only changes are committed separately from logic changes so diffs stay reviewable.
