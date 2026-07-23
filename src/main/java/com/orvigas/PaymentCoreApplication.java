package com.orvigas;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

/**
 * Payment Core platform application entry point.
 *
 * Bootstraps a fully reactive Spring WebFlux service with:
 * - Axon Framework for CQRS and event sourcing
 * - MongoDB for command-side (write) event store
 * - PostgreSQL for query-side (read) projections via R2DBC
 * - Apache Kafka for distributed event messaging
 *
 * <p>Reactor context propagation is enabled at startup so that Brave trace
 * and span IDs are automatically carried across reactive operator chains.
 *
 * @author orvigas@gmail.com
 */
@SpringBootApplication
public class PaymentCoreApplication {

    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(PaymentCoreApplication.class, args);
    }

    /**
     * Also enables context propagation through the {@link PostConstruct} path
     * so that any bean that depends on it during initialisation sees it active.
     */
    @PostConstruct
    void initContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
    }
}
