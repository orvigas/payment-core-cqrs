package com.orvigas.security.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * A configured local user for the stub login endpoint.
 *
 * @param username the login username
 * @param password the BCrypt-encoded password hash
 * @param roles    the roles granted to the user
 * @author orvigas@gmail.com
 */
public record UserProperties(
        @NotBlank String username,
        String password,
        @DefaultValue("USER") List<String> roles) {
}
