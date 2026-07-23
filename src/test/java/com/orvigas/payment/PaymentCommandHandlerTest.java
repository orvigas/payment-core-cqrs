package com.orvigas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * Unit tests for the application-level payment command handler. Idempotency
 * reservation, not a separate lookup, decides whether the aggregate gets
 * created - see {@link PaymentCommandHandler}'s Javadoc for why the
 * reservation itself must be atomic.
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
    @DisplayName("dispatches create command when the idempotency key reservation is won")
    void testWinningReservationDispatchesCreate() {
        var paymentId = PaymentId.newId();
        var command = initiateCommand(paymentId);
        when(repository.tryStore(command.idempotencyKey(), paymentId)).thenReturn(true);

        var result = handler.handle(command);

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.created()).isTrue();
        verify(commandGateway).sendAndWait(any(CreatePaymentCommand.class));
    }

    @Test
    @DisplayName("returns the existing payment id when another call already claimed the key")
    void testLosingReservationReturnsExistingPaymentId() {
        var paymentId = PaymentId.newId();
        var command = initiateCommand(paymentId);
        var existingPaymentId = PaymentId.newId();
        when(repository.tryStore(command.idempotencyKey(), paymentId)).thenReturn(false);
        when(repository.findPaymentIdByIdempotencyKey(command.idempotencyKey()))
                .thenReturn(Optional.of(existingPaymentId));

        var result = handler.handle(command);

        assertThat(result.paymentId()).isEqualTo(existingPaymentId);
        assertThat(result.created()).isFalse();
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("fails clearly if a lost reservation's winner cannot be found")
    void testLosingReservationWithNoWinnerFound() {
        var paymentId = PaymentId.newId();
        var command = initiateCommand(paymentId);
        when(repository.tryStore(command.idempotencyKey(), paymentId)).thenReturn(false);
        when(repository.findPaymentIdByIdempotencyKey(command.idempotencyKey()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(IllegalStateException.class);
        verify(commandGateway, never()).sendAndWait(any());
    }
}
