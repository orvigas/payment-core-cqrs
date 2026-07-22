package com.orvigas.security.error;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Returns an RFC 7807 problem detail for unauthenticated requests.
 *
 * @author orvigas@gmail.com
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailsAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ProblemDetailsResponseWriter writer;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return writer.write(exchange, HttpStatus.UNAUTHORIZED, "Authentication required",
                "A valid bearer token is required to access this resource.");
    }
}
