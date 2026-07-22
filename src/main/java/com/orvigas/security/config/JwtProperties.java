package com.orvigas.security.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

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

    private static final int MIN_SECRET_BYTES = 32;

    // Previously shipped as a literal default in application.yml, so it's been
    // committed to git history. A committed secret is compromised the moment it's
    // pushed per SECURITY_POLICY.md, so it must never be accepted as a real signing
    // key even if someone copies it into an env var by mistake. Deliberately does
    // NOT include the test-harness fixture secret (test-harness-application.yml) -
    // that one is a normal, reviewed test-only value, not a production fallback.
    private static final Set<String> REJECTED_PLACEHOLDER_SECRETS = Set.of(
            "changeme-secret-key-used-in-dev-only-minimum-256-bits-required");

    public JwtProperties {
        if (secret != null) {
            int byteLength = secret.getBytes(StandardCharsets.UTF_8).length;
            if (byteLength < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "JWT signing secret must be at least " + MIN_SECRET_BYTES
                                + " bytes (256 bits) for HS256; got " + byteLength);
            }
            if (REJECTED_PLACEHOLDER_SECRETS.contains(secret)) {
                throw new IllegalArgumentException(
                        "JWT signing secret matches a known placeholder value that has been "
                                + "committed to version control; generate a real secret instead.");
            }
        }
    }
}
