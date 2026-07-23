package com.orvigas.payment;

import com.orvigas.payment.idempotency.PaymentIdempotencyRepository;
import com.orvigas.shared.id.PaymentId;
import java.util.Objects;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

/**
 * Application-level command handler for payment initiation. Owns idempotency
 * exclusively: the REST layer no longer pre-checks the key, since a
 * find-then-store split across two layers just moves the race rather than
 * closing it. The key is reserved atomically before the aggregate exists, so
 * of any number of concurrent callers sharing a key, exactly one creates it.
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
     * Handles an external initiate-payment command. Reserves the idempotency
     * key atomically; if this call wins the race, the command is translated
     * to a {@link CreatePaymentCommand} and dispatched to the aggregate. If
     * another call already claimed the key - including one still in flight -
     * this call returns that winner's payment id and creates nothing.
     *
     * @param command the external initiate command
     * @return the resolved payment id and whether this call is the one that created it
     */
    @CommandHandler
    public PaymentInitiationResult handle(InitiatePaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        boolean reserved = idempotencyRepository.tryStore(command.idempotencyKey(), command.paymentId());
        if (!reserved) {
            PaymentId existing = idempotencyRepository.findPaymentIdByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "idempotency key " + command.idempotencyKey()
                                    + " is reserved by another request but its payment id could not be found"));
            return new PaymentInitiationResult(existing, false);
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
        return new PaymentInitiationResult(command.paymentId(), true);
    }
}
