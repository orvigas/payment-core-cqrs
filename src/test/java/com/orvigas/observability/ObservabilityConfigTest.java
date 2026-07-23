package com.orvigas.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the observability configuration beans apply correctly.
 *
 * @author orvigas@gmail.com
 */
class ObservabilityConfigTest {

    @Test
    void commonTagsCustomizerAppliesApplicationTag() {
        MetricsConfig config = new MetricsConfig();
        MeterRegistry registry = new SimpleMeterRegistry();

        config.commonTags().customize(registry);

        var counter = registry.counter("test.counter");
        assertThat(counter.getId().getTags())
                .contains(Tag.of("application", "payment-core"));
    }
}
