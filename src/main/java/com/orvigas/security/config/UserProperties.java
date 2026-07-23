package com.orvigas.security.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * A configured local user for the stub login endpoint.
 *
 * <p>{@code merchantId} is the tenant this user acts for. It is embedded in
 * every access token issued for the user and is the source of truth the
 * payment API checks callers against - request-body merchant ids are never
 * trusted on their own (see {@code governance/SECURITY_POLICY.md} section 2).
 *
 * @param username   the login username
 * @param password   the BCrypt-encoded password hash
 * @param roles      the roles granted to the user
 * @param merchantId the merchant this user is authorized to act for
 * @author orvigas@gmail.com
 */
public record UserProperties(
        @NotBlank String username,
        String password,
        @DefaultValue("USER") List<String> roles,
        @NotBlank String merchantId) {
}
