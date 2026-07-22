package com.orvigas.security.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Writes RFC 7807 problem details to a reactive response.
 *
 * @author orvigas@gmail.com
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailsResponseWriter {

    private static final String PROBLEM_TYPE_PREFIX = "https://api.orvigas.com/errors/";

    private final ObjectMapper objectMapper;

    /**
     * Writes a problem detail response and completes the exchange.
     *
     * @param exchange the current server exchange
     * @param status   the HTTP status to return
     * @param title    the problem title
     * @param detail   the problem detail
     * @return an empty Mono that completes when the response is written
     */
    public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, String title, String detail) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = new ProblemDetail(
                PROBLEM_TYPE_PREFIX + errorTypeFor(status),
                title,
                status.value(),
                detail,
                exchange.getRequest().getPath().value(),
                exchange.getRequest().getId());
        byte[] body = serialize(problem);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] serialize(ProblemDetail problem) {
        try {
            return objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            return fallback(problem.status());
        }
    }

    private byte[] fallback(int status) {
        return ("{\"status\":" + status + "}").getBytes(StandardCharsets.UTF_8);
    }

    private String errorTypeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "bad-request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 429 -> "too-many-requests";
            default -> "internal-server-error";
        };
    }
}
