package com.orvigas.security.support;

/**
 * Helpers for JWT tests.
 *
 * @author orvigas@gmail.com
 */
public final class JwtTestUtils {

    private JwtTestUtils() {
        // utility class
    }

    /**
     * Corrupts the payload of a compact JWS so that the signature no longer matches.
     *
     * @param token the original compact JWS
     * @return a token with the first character of the payload replaced
     */
    public static String tamperPayload(String token) {
        int firstDot = token.indexOf('.');
        return token.substring(0, firstDot + 1) + "X" + token.substring(firstDot + 2);
    }
}
