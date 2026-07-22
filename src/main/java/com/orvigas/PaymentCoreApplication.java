package com.orvigas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Payment Core platform application entry point.
 *
 * Bootstraps a fully reactive Spring WebFlux service with:
 * - Axon Framework for CQRS and event sourcing
 * - MongoDB for command-side (write) event store
 * - PostgreSQL for query-side (read) projections via R2DBC
 * - Apache Kafka for distributed event messaging
 *
 * @author orvigas@gmail.com
 */
@SpringBootApplication
public class PaymentCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentCoreApplication.class, args);
    }
}
