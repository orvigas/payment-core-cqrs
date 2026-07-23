package com.orvigas.payment.api;

import com.orvigas.payment.CapturePaymentCommand;
import com.orvigas.payment.InitiatePaymentCommand;
import com.orvigas.payment.PaymentMethod;
import com.orvigas.payment.RefundInitiator;
import com.orvigas.payment.RefundInitiatorType;
import com.orvigas.payment.RefundPaymentCommand;
import com.orvigas.payment.RefundReason;
import com.orvigas.payment.RefundReasonCode;
import com.orvigas.payment.idempotency.PaymentIdempotencyRepository;
import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Application service that translates REST API requests into Axon commands.
 * Manages idempotency for payment initiation and passes through to the
 * aggregate for captures and refunds.
 *
 * @author orvigas@gmail.com
 */
@Service
public class PaymentRestApiService {

    private static final long AUTHORIZATION_TTL_DAYS = 7;

    private final CommandGateway commandGateway;
    private final PaymentIdempotencyRepository idempotencyRepository;

    public PaymentRestApiService(
            CommandGateway commandGateway,
            PaymentIdempotencyRepository idempotencyRepository) {
        this.commandGateway = Objects.requireNonNull(commandGateway, "commandGateway must not be null");
        this.idempotencyRepository = Objects.requireNonNull(idempotencyRepository, "idempotencyRepository must not be null");
    }

    /**
     * Initiates a new payment. Returns 201 for a new payment or 200 if the
     * idempotency key was already used.
     *
     * @param request the initiate payment request
     * @return the initiation response wrapped in a ResponseEntity
     */
    public Mono<ResponseEntity<InitiatePaymentResponse>> initiatePayment(InitiatePaymentRequest request) {
        return Mono.fromCallable(() -> idempotencyRepository.findPaymentIdByIdempotencyKey(request.idempotencyKey()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existing -> {
                    if (existing.isPresent()) {
                        PaymentId pid = existing.get();
                        return Mono.just(ResponseEntity.ok(new InitiatePaymentResponse(
                                pid.value(), "INITIATED", Instant.now())));
                    }
                    return dispatchInitiate(request, PaymentId.newId());
                });
    }

    /**
     * Requests a capture of authorized funds.
     *
     * @param paymentId the payment identifier
     * @param request   the capture request
     * @return the capture response
     */
    public Mono<CapturePaymentResponse> capturePayment(PaymentId paymentId, CapturePaymentRequest request) {
        CaptureId captureId = CaptureId.newId();
        Money amount = toMoney(request.amount());
        CapturePaymentCommand command = new CapturePaymentCommand(paymentId, amount, request.isFinal(), captureId);

        return Mono.fromFuture(commandGateway.send(command))
                .then(Mono.just(new CapturePaymentResponse(
                        paymentId.value(),
                        captureId.value(),
                        "CAPTURED",
                        new MoneyResponse(amount.minorUnits(), amount.currency().getCurrencyCode()))));
    }

    /**
     * Requests a refund of previously captured funds.
     *
     * @param paymentId the payment identifier
     * @param request   the refund request
     * @return the refund response
     */
    public Mono<RefundPaymentResponse> refundPayment(PaymentId paymentId, RefundPaymentRequest request) {
        RefundId refundId = RefundId.newId();
        Money amount = toMoney(request.amount());
        RefundReason reason = new RefundReason(
                RefundReasonCode.valueOf(request.reason().code()),
                request.reason().description());
        RefundInitiator initiatedBy = new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "api");
        RefundPaymentCommand command = new RefundPaymentCommand(
                paymentId, amount, null, reason, request.idempotencyKey(), initiatedBy, refundId);

        return Mono.fromFuture(commandGateway.send(command))
                .then(Mono.just(new RefundPaymentResponse(
                        paymentId.value(),
                        refundId.value(),
                        "REFUND_REQUESTED",
                        new MoneyResponse(amount.minorUnits(), amount.currency().getCurrencyCode()))));
    }

    private Mono<ResponseEntity<InitiatePaymentResponse>> dispatchInitiate(
            InitiatePaymentRequest request, PaymentId paymentId) {
        Money amount = toMoney(request.amount());
        InitiatePaymentCommand command = new InitiatePaymentCommand(
                paymentId,
                MerchantId.fromString(request.merchantId()),
                CustomerId.fromString(request.customerId()),
                amount,
                new PaymentMethod(request.paymentMethodToken()),
                request.idempotencyKey(),
                Instant.now().plus(AUTHORIZATION_TTL_DAYS, ChronoUnit.DAYS));

        return Mono.fromFuture(commandGateway.send(command))
                .map(result -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new InitiatePaymentResponse(
                                paymentId.value(),
                                "INITIATED",
                                Instant.now())));
    }

    private static Money toMoney(MoneyRequest request) {
        return Money.of(request.minorUnits(), request.currency());
    }
}
