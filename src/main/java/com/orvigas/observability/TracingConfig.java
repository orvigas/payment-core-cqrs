package com.orvigas.observability;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * Enables Reactor context propagation for distributed tracing.
 *
 * <p>Without {@link Hooks#enableAutomaticContextPropagation()} the trace context
 * is lost across reactive operator boundaries, making every span orphaned.
 * This configuration is applied eagerly at startup so Brave's trace and span IDs
 * flow through the reactive chain without manual propagation.
 *
 * @author orvigas@gmail.com
 */
@Configuration
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    /**
     * Enables automatic Reactor context propagation so that Brave trace and
     * span IDs flow through reactive operator chains without manual handling.
     */
    @PostConstruct
    public void enableContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
        log.info("Reactor automatic context propagation enabled for distributed tracing");
    }
}
