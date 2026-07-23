package com.orvigas.payment.api;

import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller for payment operations: initiate, capture, and refund.
 *
 * <p>Every endpoint requires the {@code USER} role via a bearer JWT and acts
 * only on the merchant carried in that token's {@code merchantId} claim -
 * never on a merchant id taken from the request body or path.
 *
 * @author orvigas@gmail.com
 */
@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Payment processing operations")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentRestApiService paymentService;

    /**
     * Creates the controller.
     *
     * @param paymentService application service that dispatches the underlying commands
     */
    public PaymentController(PaymentRestApiService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Initiates a new payment. Idempotent via the client-supplied key: a new
     * payment returns 201, a duplicate returns 200. Requires the {@code USER}
     * role; the caller may only initiate payments for their own merchant.
     *
     * @param request the initiate payment request
     * @param jwt      the caller's verified token
     * @return the initiation response
     */
    @PostMapping
    @Operation(summary = "Initiate a new payment", description = "Requires the USER role. "
            + "The caller may only initiate payments for the merchant in their own token.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InitiatePaymentResponse.class))),
            @ApiResponse(responseCode = "200", description = "Duplicate idempotency key, existing payment returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InitiatePaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Caller's merchant does not match the requested merchant"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public Mono<ResponseEntity<InitiatePaymentResponse>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.initiatePayment(request, callerMerchantId(jwt));
    }

    /**
     * Captures authorized funds. Requires the {@code USER} role; the caller
     * may only capture payments owned by their own merchant.
     *
     * @param paymentId the payment identifier
     * @param request   the capture request
     * @param jwt       the caller's verified token
     * @return the capture response
     */
    @PostMapping("/{paymentId}/captures")
    @Operation(summary = "Capture authorized funds", description = "Requires the USER role. "
            + "The caller may only capture payments owned by their own merchant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Capture requested",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CapturePaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or invariant violation"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Payment not found, or not owned by the caller's merchant"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public Mono<CapturePaymentResponse> capturePayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody CapturePaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.capturePayment(PaymentId.of(paymentId), request, callerMerchantId(jwt));
    }

    /**
     * Refunds previously captured funds. Requires the {@code USER} role; the
     * caller may only refund payments owned by their own merchant.
     *
     * @param paymentId the payment identifier
     * @param request   the refund request
     * @param jwt       the caller's verified token
     * @return the refund response
     */
    @PostMapping("/{paymentId}/refunds")
    @Operation(summary = "Refund captured funds", description = "Requires the USER role. "
            + "The caller may only refund payments owned by their own merchant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refund requested",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RefundPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or invariant violation"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Payment not found, or not owned by the caller's merchant"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public Mono<RefundPaymentResponse> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.refundPayment(PaymentId.of(paymentId), request, callerMerchantId(jwt));
    }

    private MerchantId callerMerchantId(Jwt jwt) {
        return MerchantId.fromString(jwt.getClaimAsString("merchantId"));
    }
}
