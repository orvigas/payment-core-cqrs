package com.orvigas.payment.api;

import com.orvigas.payment.CapturePaymentCommand;
import com.orvigas.payment.CaptureStatus;
import com.orvigas.payment.InitiatePaymentCommand;
import com.orvigas.payment.PaymentInitiationResult;
import com.orvigas.payment.PaymentMethod;
import com.orvigas.payment.RefundInitiator;
import com.orvigas.payment.RefundInitiatorType;
import com.orvigas.payment.RefundPaymentCommand;
import com.orvigas.payment.RefundReason;
import com.orvigas.payment.RefundReasonCode;
import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import com.orvigas.shared.money.Money;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Application service that translates REST API requests into Axon commands.
 *
 * <p>Every method takes the caller's merchant id as resolved from their JWT,
 * never from the request body or path alone - see
 * {@code governance/SECURITY_POLICY.md} section 2. Initiation checks it
 * against the requested merchant directly; captures and refunds pass it
 * through to the aggregate, which is the only place that knows a path-
 * supplied payment id's real owner.
 *
 * @author orvigas@gmail.com
 */
@Service
public class PaymentRestApiService {

    private static final long AUTHORIZATION_TTL_DAYS = 7;

    private final CommandGateway commandGateway;

    /**
     * Creates the service.
     *
     * @param commandGateway gateway used to dispatch commands to the Payment aggregate
     */
    public PaymentRestApiService(CommandGateway commandGateway) {
        this.commandGateway = Objects.requireNonNull(commandGateway, "commandGateway must not be null");
    }

    /**
     * Initiates a new payment. Returns 201 for a new payment or 200 if the
     * idempotency key was already used; idempotency is enforced exclusively
     * by the command handler, not pre-checked here.
     *
     * @param request          the initiate payment request
     * @param callerMerchantId the merchant the caller is authorized to act for
     * @return the initiation response wrapped in a ResponseEntity
     * @throws AccessDeniedException if the request targets a different merchant than the caller
     */
    @RateLimiter(name = "#callerMerchantId.value() + '-payment'")
    public Mono<ResponseEntity<InitiatePaymentResponse>> initiatePayment(
            InitiatePaymentRequest request, MerchantId callerMerchantId) {
        MerchantId requestedMerchantId = MerchantId.fromString(request.merchantId());
        if (!requestedMerchantId.equals(callerMerchantId)) {
            return Mono.error(new AccessDeniedException(
                    "caller's merchant does not match the requested merchant"));
        }

        Money amount = toMoney(request.amount());
        InitiatePaymentCommand command = new InitiatePaymentCommand(
                PaymentId.newId(),
                requestedMerchantId,
                CustomerId.fromString(request.customerId()),
                amount,
                new PaymentMethod(request.paymentMethodToken()),
                request.idempotencyKey(),
                Instant.now().plus(AUTHORIZATION_TTL_DAYS, ChronoUnit.DAYS));

        return Mono.defer(() -> Mono.fromFuture(commandGateway.<PaymentInitiationResult>send(command)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                        .body(new InitiatePaymentResponse(
                                result.paymentId().value(), "INITIATED", Instant.now())));
    }

    /**
     * Requests a capture of authorized funds.
     *
     * @param paymentId        the payment identifier
     * @param request          the capture request
     * @param callerMerchantId the merchant the caller is authorized to act for
     * @return the capture response, reflecting the capture's actual post-command status
     */
    @RateLimiter(name = "#callerMerchantId.value() + '-payment'")
    public Mono<CapturePaymentResponse> capturePayment(
            PaymentId paymentId, CapturePaymentRequest request, MerchantId callerMerchantId) {
        CaptureId captureId = CaptureId.newId();
        Money amount = toMoney(request.amount());
        CapturePaymentCommand command = new CapturePaymentCommand(
                paymentId, amount, request.isFinal(), captureId, callerMerchantId);

        return Mono.defer(() -> Mono.fromFuture(commandGateway.<CaptureStatus>send(command)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(status -> new CapturePaymentResponse(
                        paymentId.value(),
                        captureId.value(),
                        status.name(),
                        new MoneyResponse(amount.minorUnits(), amount.currency().getCurrencyCode())));
    }

    /**
     * Requests a refund of previously captured funds.
     *
     * @param paymentId        the payment identifier
     * @param request          the refund request
     * @param callerMerchantId the merchant the caller is authorized to act for
     * @return the refund response, built from the refund id the aggregate actually resolved to
     */
    @RateLimiter(name = "#callerMerchantId.value() + '-payment'")
    public Mono<RefundPaymentResponse> refundPayment(
            PaymentId paymentId, RefundPaymentRequest request, MerchantId callerMerchantId) {
        Money amount = toMoney(request.amount());
        RefundReason reason = new RefundReason(
                RefundReasonCode.valueOf(request.reason().code()),
                request.reason().description());
        RefundInitiator initiatedBy = new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "api");
        RefundPaymentCommand command = new RefundPaymentCommand(
                paymentId, amount, null, reason, request.idempotencyKey(), initiatedBy, null, callerMerchantId);

        return Mono.defer(() -> Mono.fromFuture(commandGateway.<RefundId>send(command)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(refundId -> new RefundPaymentResponse(
                        paymentId.value(),
                        refundId.value(),
                        "REFUND_REQUESTED",
                        new MoneyResponse(amount.minorUnits(), amount.currency().getCurrencyCode())));
    }

    private static Money toMoney(MoneyRequest request) {
        return Money.of(request.minorUnits(), request.currency());
    }
}
