package com.orvigas.security.error;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * Translates application exceptions into RFC 7807 problem details.
 *
 * @author orvigas@gmail.com
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalErrorHandler {

    private static final String PROBLEM_TYPE_PREFIX = "https://api.orvigas.com/errors/";

    @ExceptionHandler(BadCredentialsException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleBadCredentials(ServerWebExchange exchange) {
        return Mono.just(unauthorized(exchange, "Invalid credentials"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleAuthentication(ServerWebExchange exchange) {
        return Mono.just(unauthorized(exchange, "Authentication failed"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleAccessDenied(ServerWebExchange exchange) {
        return Mono.just(forbidden(exchange, "Access denied"));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleWebExchangeBindException(ServerWebExchange exchange) {
        return Mono.just(badRequest(exchange, "Invalid request content"));
    }

    @ExceptionHandler({ServerWebInputException.class, ConstraintViolationException.class})
    public Mono<ResponseEntity<ProblemDetail>> handleValidation(ServerWebExchange exchange, Exception ex) {
        // Jackson's parse-failure messages can embed the offending input or internal
        // parser detail, so the client only ever gets a fixed string; the real cause
        // goes to the server log against the request's correlation id.
        log.warn("Rejected malformed request body [{}]: {}",
                exchange.getRequest().getId(), ex.getClass().getSimpleName(), ex);
        return Mono.just(badRequest(exchange, "Invalid request content"));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public Mono<ResponseEntity<ProblemDetail>> handleRateLimit(ServerWebExchange exchange) {
        return Mono.just(tooManyRequests(exchange, "Rate limit exceeded. Please try again later."));
    }

    private ResponseEntity<ProblemDetail> unauthorized(ServerWebExchange exchange, String detail) {
        return problem(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }

    private ResponseEntity<ProblemDetail> forbidden(ServerWebExchange exchange, String detail) {
        return problem(exchange, HttpStatus.FORBIDDEN, "Forbidden", detail);
    }

    private ResponseEntity<ProblemDetail> badRequest(ServerWebExchange exchange, String detail) {
        return problem(exchange, HttpStatus.BAD_REQUEST, "Bad request", detail);
    }

    private ResponseEntity<ProblemDetail> tooManyRequests(ServerWebExchange exchange, String detail) {
        return problem(exchange, HttpStatus.TOO_MANY_REQUESTS, "Too many requests", detail);
    }

    private ResponseEntity<ProblemDetail> problem(ServerWebExchange exchange, HttpStatus status, String title, String detail) {
        ProblemDetail body = new ProblemDetail(
                PROBLEM_TYPE_PREFIX + errorTypeFor(status),
                title,
                status.value(),
                detail,
                exchange.getRequest().getPath().value(),
                exchange.getRequest().getId());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private String errorTypeFor(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "bad-request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 429 -> "too-many-requests";
            default -> "internal-server-error";
        };
    }
}
