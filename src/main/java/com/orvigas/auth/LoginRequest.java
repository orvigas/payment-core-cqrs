package com.orvigas.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request body.
 *
 * @param username the username
 * @param password the password
 * @author orvigas@gmail.com
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Size(max = 255) String password) {
}
