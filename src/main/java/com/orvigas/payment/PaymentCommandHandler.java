package com.orvigas.payment;

import com.orvigas.payment.idempotency.PaymentIdempotencyRepository;
import com.orvigas.shared.id.PaymentId;
import java.util.Objects;
import java.util.Optional;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
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

    private final PaymentIdempotencyRepository idempotencyRepository;
    private final CommandGateway commandGateway;

    public PaymentCommandHandler(
            PaymentIdempotencyRepository idempotencyRepository,
            CommandGateway commandGateway) {
        this.idempotencyRepository = Objects.requireNonNull(idempotencyRepository, "idempotencyRepository must not be null");
        this.commandGateway = Objects.requireNonNull(commandGateway, "commandGateway must not be null");
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
        Objects.requireNonNull(command, "command must not be null");
        Optional<PaymentId> existing = idempotencyRepository.findPaymentIdByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
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
        return command.paymentId();
    }
}
