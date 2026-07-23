package com.orvigas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orvigas.payment.idempotency.PaymentIdempotencyRepository;
import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.Optional;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the application-level payment command handler.
 *
 * @author orvigas@gmail.com
 */
@DisplayName("Payment command handler")
class PaymentCommandHandlerTest {

    private final PaymentIdempotencyRepository repository = mock(PaymentIdempotencyRepository.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final PaymentCommandHandler handler = new PaymentCommandHandler(repository, commandGateway);

    private InitiatePaymentCommand initiateCommand(PaymentId paymentId) {
        return new InitiatePaymentCommand(
                paymentId,
                MerchantId.newId(),
                CustomerId.newId(),
                Money.of(10000, "USD"),
                new PaymentMethod("tok_visa"),
                "idempotency-key-1",
                Instant.now().plusSeconds(86400 * 7));
    }

    @Test
    @DisplayName("dispatches create command for a new idempotency key")
    void testNewIdempotencyKeyDispatchesCreate() {
        var paymentId = PaymentId.newId();
        var command = initiateCommand(paymentId);
        when(repository.findPaymentIdByIdempotencyKey(command.idempotencyKey())).thenReturn(Optional.empty());

        var result = handler.handle(command);

        assertThat(result).isEqualTo(paymentId);
        verify(commandGateway).sendAndWait(any(CreatePaymentCommand.class));
        verify(repository).store(command.idempotencyKey(), paymentId);
    }

    @Test
    @DisplayName("returns existing payment id for a duplicate idempotency key")
    void testDuplicateIdempotencyKeyReturnsExistingPaymentId() {
        var paymentId = PaymentId.newId();
        var command = initiateCommand(paymentId);
        var existingPaymentId = PaymentId.newId();
        when(repository.findPaymentIdByIdempotencyKey(command.idempotencyKey()))
                .thenReturn(Optional.of(existingPaymentId));

        var result = handler.handle(command);

        assertThat(result).isEqualTo(existingPaymentId);
        verify(commandGateway, never()).sendAndWait(any());
        verify(repository, never()).store(any(), any());
    }
}
