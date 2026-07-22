package com.orvigas.security.error;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * Translates controller and service exceptions into RFC 7807 problem details.
 *
 * <p>Authentication and authorization failures raised by the security filter
 * chain itself never reach this class - they're handled directly by
 * {@link ProblemDetailsAuthenticationEntryPoint} and
 * {@link ProblemDetailsAccessDeniedHandler}, wired into {@code SecurityConfig}
 * ahead of the controller layer. This advice only covers exceptions that
 * originate from controller or service logic. Response bodies are built
 * through {@link ProblemDetailsResponseWriter} so both layers share one
 * source of truth for the RFC 7807 shape.
 *
 * @author orvigas@gmail.com
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalErrorHandler {

    private final ProblemDetailsResponseWriter writer;

    @ExceptionHandler(BadCredentialsException.class)
    public Mono<Void> handleBadCredentials(ServerWebExchange exchange) {
        return writer.write(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid credentials");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<Void> handleWebExchangeBindException(ServerWebExchange exchange) {
        return writer.write(exchange, HttpStatus.BAD_REQUEST, "Bad request", "Invalid request content");
    }

    @ExceptionHandler({ServerWebInputException.class, ConstraintViolationException.class})
    public Mono<Void> handleValidation(ServerWebExchange exchange, Exception ex) {
        // Jackson's parse-failure messages can embed the offending input or internal
        // parser detail, so the client only ever gets a fixed string; the real cause
        // goes to the server log against the request's correlation id.
        log.warn("Rejected malformed request body [{}]: {}",
                exchange.getRequest().getId(), ex.getClass().getSimpleName(), ex);
        return writer.write(exchange, HttpStatus.BAD_REQUEST, "Bad request", "Invalid request content");
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public Mono<Void> handleRateLimit(ServerWebExchange exchange) {
        return writer.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                "Rate limit exceeded. Please try again later.");
    }
}
