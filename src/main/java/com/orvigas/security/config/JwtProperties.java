package com.orvigas.security.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * JWT signing and validation settings.
 *
 * @param secret   the HMAC-SHA256 signing secret (at least 256 bits)
 * @param expiration token lifetime
 * @param issuer   the token issuer claim
 * @param audience the token audience claim
 * @author orvigas@gmail.com
 */
public record JwtProperties(
        @NotBlank String secret,
        @DefaultValue("15m") Duration expiration,
        @DefaultValue("payment-core") String issuer,
        @DefaultValue("payment-core") String audience) {
}
