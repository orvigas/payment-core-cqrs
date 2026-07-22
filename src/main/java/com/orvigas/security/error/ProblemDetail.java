package com.orvigas.security.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 7807 Problem Details response body.
 *
 * @param type          a URI that identifies the problem type
 * @param title         a short, human-readable summary
 * @param status        the HTTP status code
 * @param detail        a human-readable explanation
 * @param instance      the request path that produced the problem
 * @param correlationId the request correlation identifier
 * @author orvigas@gmail.com
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String correlationId) {
}
