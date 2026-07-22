package com.orvigas.security.error;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Returns an RFC 7807 problem detail for forbidden requests.
 *
 * @author orvigas@gmail.com
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailsAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ProblemDetailsResponseWriter writer;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return writer.write(exchange, HttpStatus.FORBIDDEN, "Access denied",
                "The caller lacks permission to access this resource.");
    }
}
