package com.orvigas.auth;

/**
 * Login response containing the issued JWT.
 *
 * @param accessToken the signed JWT access token
 * @param tokenType   the token type, always {@code Bearer}
 * @param expiresIn   token lifetime in seconds
 * @author orvigas@gmail.com
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
