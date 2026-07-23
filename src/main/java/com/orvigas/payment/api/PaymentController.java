package com.orvigas.payment.api;

import com.orvigas.shared.id.PaymentId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller for payment operations: initiate, capture, and refund.
 *
 * @author orvigas@gmail.com
 */
@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Payment processing operations")
public class PaymentController {

    private final PaymentRestApiService paymentService;

    public PaymentController(PaymentRestApiService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Initiates a new payment. Idempotent via the client-supplied key: a new
     * payment returns 201, a duplicate returns 200.
     *
     * @param request the initiate payment request
     * @return the initiation response
     */
    @PostMapping
    @Operation(summary = "Initiate a new payment")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InitiatePaymentResponse.class))),
            @ApiResponse(responseCode = "200", description = "Duplicate idempotency key, existing payment returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InitiatePaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public Mono<ResponseEntity<InitiatePaymentResponse>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        return paymentService.initiatePayment(request);
    }

    /**
     * Captures authorized funds.
     *
     * @param paymentId the payment identifier
     * @param request   the capture request
     * @return the capture response
     */
    @PostMapping("/{paymentId}/captures")
    @Operation(summary = "Capture authorized funds")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Capture requested",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CapturePaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or invariant violation"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public Mono<CapturePaymentResponse> capturePayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody CapturePaymentRequest request) {
        return paymentService.capturePayment(PaymentId.of(paymentId), request);
    }

    /**
     * Refunds previously captured funds.
     *
     * @param paymentId the payment identifier
     * @param request   the refund request
     * @return the refund response
     */
    @PostMapping("/{paymentId}/refunds")
    @Operation(summary = "Refund captured funds")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refund requested",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RefundPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or invariant violation"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public Mono<RefundPaymentResponse> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundPaymentRequest request) {
        return paymentService.refundPayment(PaymentId.of(paymentId), request);
    }
}
