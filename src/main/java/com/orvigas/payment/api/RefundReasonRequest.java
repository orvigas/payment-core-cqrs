package com.orvigas.payment.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Structured reason for a refund request.
 *
 * @param code        structured reason code (e.g., "REQUESTED_BY_CUSTOMER", "DUPLICATE")
 * @param description optional free-text explanation
 * @author orvigas@gmail.com
 */
public record RefundReasonRequest(
        @NotBlank String code,
        String description) {
}
