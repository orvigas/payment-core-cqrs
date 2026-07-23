package com.orvigas.payment;

import com.orvigas.observability.PaymentMetrics;
import com.orvigas.payment.idempotency.PaymentIdempotencyRepository;
import com.orvigas.shared.id.PaymentId;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.Optional;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Application-level command handler for payment initiation. Enforces
 * idempotency before the aggregate is created: duplicate keys return the
 * existing payment identifier without creating a second aggregate.
 *
 * @author orvigas@gmail.com
 */
@Component
public class PaymentCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandHandler.class);

    private final PaymentIdempotencyRepository idempotencyRepository;
    private final CommandGateway commandGateway;
    private final PaymentMetrics paymentMetrics;

    public PaymentCommandHandler(
            PaymentIdempotencyRepository idempotencyRepository,
            CommandGateway commandGateway,
            PaymentMetrics paymentMetrics) {
        this.idempotencyRepository = Objects.requireNonNull(idempotencyRepository, "idempotencyRepository must not be null");
        this.commandGateway = Objects.requireNonNull(commandGateway, "commandGateway must not be null");
        this.paymentMetrics = Objects.requireNonNull(paymentMetrics, "paymentMetrics must not be null");
    }

    /**
     * Handles an external initiate-payment command. If the idempotency key
     * has been used before, the existing payment identifier is returned and
     * no aggregate is created. Otherwise the command is translated to a
     * {@link CreatePaymentCommand} and dispatched to the aggregate.
     *
     * @param command the external initiate command
     * @return the payment identifier for this key
     */
    @CommandHandler
    public PaymentId handle(InitiatePaymentCommand command) {
        Timer.Sample sample = paymentMetrics.startTimer();
        Objects.requireNonNull(command, "command must not be null");
        Optional<PaymentId> existing = idempotencyRepository.findPaymentIdByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            paymentMetrics.stopTimer(sample);
            return existing.get();
        }

        CreatePaymentCommand createCommand = new CreatePaymentCommand(
                command.paymentId(),
                command.merchantId(),
                command.customerId(),
                command.amount(),
                command.paymentMethod(),
                command.idempotencyKey(),
                command.authorizationExpiresAt());

        commandGateway.sendAndWait(createCommand);
        idempotencyRepository.store(command.idempotencyKey(), command.paymentId());
        paymentMetrics.recordPaymentInitiated();
        paymentMetrics.stopTimer(sample);
        log.debug("Payment initiated: {}", command.paymentId());
        return command.paymentId();
    }
}
