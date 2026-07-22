package com.orvigas.security.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for the JWT signing secret's eager validation.
 *
 * @author orvigas@gmail.com
 */
class JwtPropertiesTest {

    private static final String VALID_SECRET = "a-valid-secret-key-that-is-at-least-32-bytes-long";

    @Test
    void acceptsASecretAtLeast32BytesLong() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, Duration.ofMinutes(15), "issuer", "audience");

        assertThat(properties.secret()).isEqualTo(VALID_SECRET);
    }

    @Test
    void rejectsASecretShorterThan32Bytes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties("too-short", Duration.ofMinutes(15), "issuer", "audience"))
                .withMessageContaining("256 bits");
    }

    @Test
    void rejectsTheLeakedDevelopmentPlaceholder() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties(
                        "changeme-secret-key-used-in-dev-only-minimum-256-bits-required",
                        Duration.ofMinutes(15), "issuer", "audience"))
                .withMessageContaining("known placeholder");
    }
}
