package com.orvigas.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Security-related configuration bound from {@code com.orvigas.security.*}.
 *
 * @param jwt   JWT signing and validation settings
 * @param users map of configured local users keyed by an internal identifier
 * @author orvigas@gmail.com
 */
@ConfigurationProperties(prefix = "com.orvigas.security")
@Validated
public record SecurityProperties(
        @Valid JwtProperties jwt,
        @DefaultValue Map<String, @Valid UserProperties> users) {
}
