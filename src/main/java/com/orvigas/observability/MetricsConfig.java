package com.orvigas.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures global Micrometer meter registry customizations.
 *
 * @author orvigas@gmail.com
 */
@Configuration
public class MetricsConfig {

    /**
     * Tags every metric emitted by this application with {@code application=payment-core}.
     *
     * @return the customizer to apply
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config().commonTags("application", "payment-core");
    }
}
