package com.orvigas.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.reactivestreams.Publisher;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Enforces the binding rules from governance/ARCHITECTURE_RULES.md and
 * governance/CODING_STANDARD.md as part of the build. A violation here fails
 * mvn verify, so these rules cannot silently erode as the codebase grows.
 *
 * @author orvigas@gmail.com
 */
@AnalyzeClasses(packages = "com.orvigas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    // Layers are optional while the codebase is young; the rule tightens
    // automatically as packages appear.
    @ArchTest
    static final ArchRule oneWayLayering = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Controller").definedBy("..controller..", "..api..")
            .layer("Service").definedBy("..service..", "..application..")
            .layer("Repository").definedBy("..repository..", "..projection..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .because("dependencies flow one way: controller -> service -> repository");

    // allowEmptyShould: until the first class with fields exists, this rule
    // has nothing to check and would fail the build on emptiness alone.
    @ArchTest
    static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("constructor injection only, preferably via @RequiredArgsConstructor")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noJpaOrHibernate = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "javax.persistence..", "org.hibernate..")
            .because("persistence is Axon/MongoDB on the write side and R2DBC on the read side; JPA is banned");

    // Catches block(), blockFirst(), blockLast(), blockOptional() and manual
    // subscribe() on Mono/Flux. Kafka listeners are the sanctioned blocking
    // exception, but they consume typed payloads and have no reason to block
    // a Publisher either.
    @ArchTest
    static final ArchRule noBlockingOnReactiveTypes = noClasses()
            .should().callMethodWhere(
                    target(owner(assignableTo(Publisher.class)))
                            .and(target(nameMatching("block.*|subscribe"))))
            .because("the request path is non-blocking; return the Mono/Flux and let the framework subscribe");

    @ArchTest
    static final ArchRule noThreadLocalSecurityContext = noClasses()
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.security.core.context.SecurityContextHolder")
            .because("auth context must propagate via the Reactor context, not thread-locals");

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("use SLF4J structured logging");

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
            .because("use SLF4J structured logging");
}
