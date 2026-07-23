package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.id.RefundId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

/**
 * Payment aggregate root. Represents a single payment attempt from customer to
 * merchant, including authorization, capture, and refund lifecycles. Immutable
 * value objects for commands/events; mutable state is rebuilt by replaying
 * events.
 *
 * @author orvigas@gmail.com
 */
@Aggregate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Setter(AccessLevel.PRIVATE)
public class Payment {

    @AggregateIdentifier
    private PaymentId paymentId;

    private MerchantId merchantId;
    private CustomerId customerId;
    private Money amount;
    private Money authorizedAmount;
    private PaymentMethod paymentMethod;
    private String idempotencyKey;
    private String authorizationCode;
    private FailureReason failureReason;
    private Instant authorizationExpiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private PaymentStatus status;

    private final Map<CaptureId, Capture> captures = new HashMap<>();

    private final Map<RefundId, Refund> refunds = new HashMap<>();

    private Money capturedAmount;
    private Money refundedAmount;
    private final List<String> idempotencyKeysForRefunds = new ArrayList<>();
    private boolean finalCaptureSucceeded;

    /**
     * Constructor that handles the initiate payment command.
     *
     * @param command the initiate command
     */
    @CommandHandler
    public Payment(CreatePaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        AggregateLifecycle.apply(
                new PaymentInitiated(
                        command.paymentId(),
                        command.merchantId(),
                        command.customerId(),
                        command.amount(),
                        command.paymentMethod(),
                        command.idempotencyKey(),
                        command.authorizationExpiresAt(),
                        Instant.now()));
    }

    /**
     * Handles the authorize payment command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(AuthorizePaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status != PaymentStatus.INITIATED) {
            throw new IllegalStateException(
                    "payment must be in INITIATED state to authorize, current status: " + status);
        }

        Instant now = Instant.now();
        if (command.authorizationCode() != null) {
            AggregateLifecycle.apply(
                    new PaymentAuthorized(
                            paymentId,
                            command.authorizedAmount(),
                            command.authorizationCode(),
                            null,
                            now));
        } else {
            AggregateLifecycle.apply(
                    new PaymentAuthorized(
                            paymentId,
                            command.authorizedAmount(),
                            null,
                            command.failureReason(),
                            now));
            AggregateLifecycle.apply(
                    new PaymentFailed(paymentId, command.failureReason(), now));
        }
    }

    /**
     * Handles the capture payment command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(CapturePaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status != PaymentStatus.AUTHORIZED && status != PaymentStatus.PARTIALLY_CAPTURED) {
            throw new IllegalStateException(
                    "payment must be AUTHORIZED or PARTIALLY_CAPTURED to capture, current status: " + status);
        }

        if (finalCaptureSucceeded) {
            throw new IllegalStateException("a final capture has already succeeded for this payment");
        }

        if (!command.amount().isPositive()) {
            throw new IllegalArgumentException("capture amount must be positive");
        }

        Money totalCaptured = capturedAmount.add(command.amount());
        for (Capture capture : captures.values()) {
            if (capture.getStatus() == CaptureStatus.PENDING) {
                totalCaptured = totalCaptured.add(capture.getAmount());
            }
        }
        if (totalCaptured.compareTo(authorizedAmount) > 0) {
            throw new IllegalStateException(
                    "captured amount " + totalCaptured.minorUnits() + " exceeds authorized amount "
                            + authorizedAmount.minorUnits());
        }

        if (authorizationExpiresAt.isBefore(Instant.now())) {
            throw new IllegalStateException("authorization has expired");
        }

        CaptureId captureId = CaptureId.newId();
        Instant now = Instant.now();
        AggregateLifecycle.apply(
                new PaymentCharged(paymentId, captureId, command.amount(), command.isFinal(), now));
    }

    /**
     * Handles the complete payment command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(CompletePaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status != PaymentStatus.CAPTURED && status != PaymentStatus.PARTIALLY_CAPTURED) {
            throw new IllegalStateException(
                    "payment must be CAPTURED or PARTIALLY_CAPTURED to complete, current status: " + status);
        }

        Instant now = Instant.now();
        AggregateLifecycle.apply(new PaymentCompleted(paymentId, now));
    }

    /**
     * Handles the fail payment command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(FailPaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (isTerminalState(status)) {
            throw new IllegalStateException("payment is in terminal state: " + status);
        }

        Instant now = Instant.now();
        AggregateLifecycle.apply(new PaymentFailed(paymentId, command.reason(), now));
    }

    /**
     * Handles the expire payment command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(ExpirePaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "payment must be AUTHORIZED to expire, current status: " + status);
        }

        if (!authorizationExpiresAt.isBefore(Instant.now())) {
            throw new IllegalStateException("authorization has not yet expired");
        }

        Instant now = Instant.now();
        AggregateLifecycle.apply(new PaymentExpired(paymentId, now));
    }

    /**
     * Handles the refund payment command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(RefundPaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!canRefund()) {
            throw new IllegalStateException("payment in state " + status + " does not allow refunds");
        }

        if (!command.amount().isPositive()) {
            throw new IllegalArgumentException("refund amount must be positive");
        }

        Refund existingRefund = findRefundByIdempotencyKey(command.idempotencyKey());
        if (existingRefund != null) {
            if (!existingRefund.getAmount().equals(command.amount())) {
                throw new IllegalStateException(
                        "idempotency key " + command.idempotencyKey()
                                + " already used with a different amount");
            }
            return;
        }

        Money totalRefunding = refundedAmount.add(command.amount());
        for (Refund refund : refunds.values()) {
            if (refund.getStatus() == RefundStatus.REQUESTED || refund.getStatus() == RefundStatus.PENDING) {
                totalRefunding = totalRefunding.add(refund.getAmount());
            }
        }

        if (totalRefunding.compareTo(capturedAmount) > 0) {
            throw new IllegalStateException(
                    "refunding " + command.amount().minorUnits() + " would exceed captured amount "
                            + capturedAmount.minorUnits());
        }

        RefundId refundId = RefundId.newId();
        Instant now = Instant.now();
        AggregateLifecycle.apply(
                new RefundRequested(
                        paymentId,
                        refundId,
                        command.amount(),
                        command.captureId(),
                        command.reason(),
                        command.idempotencyKey(),
                        command.initiatedBy(),
                        now));
    }

    /**
     * Handles the confirm capture command (internal).
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(ConfirmCaptureCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Capture capture = captures.get(command.captureId());
        if (capture == null) {
            throw new IllegalStateException("capture not found: " + command.captureId());
        }

        if (capture.getStatus() != CaptureStatus.PENDING) {
            throw new IllegalStateException("capture must be PENDING to confirm");
        }

        Instant now = Instant.now();
        AggregateLifecycle.apply(
                new CaptureSucceeded(paymentId, command.captureId(), command.providerReference(), now));
    }

    /**
     * Handles the fail capture command (internal).
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(FailCaptureCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Capture capture = captures.get(command.captureId());
        if (capture == null) {
            throw new IllegalStateException("capture not found: " + command.captureId());
        }

        if (capture.getStatus() != CaptureStatus.PENDING) {
            throw new IllegalStateException("capture must be PENDING to fail");
        }

        Instant now = Instant.now();
        AggregateLifecycle.apply(
                new CaptureFailed(paymentId, command.captureId(), command.reason(), now));
    }

    /**
     * Handles the mark refund pending command (internal).
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(MarkRefundPendingCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Refund refund = refunds.get(command.refundId());
        if (refund == null) {
            throw new IllegalStateException("refund not found: " + command.refundId());
        }

        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw new IllegalStateException("refund must be REQUESTED to mark pending, current status: " + refund.getStatus());
        }

        Instant now = Instant.now();
        AggregateLifecycle.apply(new RefundPending(paymentId, command.refundId(), now));
    }

    /**
     * Handles the confirm refund command (internal).
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(ConfirmRefundCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Refund refund = refunds.get(command.refundId());
        if (refund == null) {
            throw new IllegalStateException("refund not found: " + command.refundId());
        }

        if (refund.getStatus() != RefundStatus.REQUESTED && refund.getStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException("refund must be REQUESTED or PENDING to confirm");
        }

        Instant now = Instant.now();
        AggregateLifecycle.apply(
                new RefundSucceeded(paymentId, command.refundId(), command.providerReference(), now));
    }

    /**
     * Handles the fail refund command (internal).
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(FailRefundCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Refund refund = refunds.get(command.refundId());
        if (refund == null) {
            throw new IllegalStateException("refund not found: " + command.refundId());
        }

        if (refund.getStatus() == RefundStatus.SUCCEEDED || refund.getStatus() == RefundStatus.FAILED) {
            throw new IllegalStateException(
                    "refund cannot be failed in status " + refund.getStatus());
        }

        Instant now = Instant.now();
        AggregateLifecycle.apply(
                new RefundFailed(paymentId, command.refundId(), command.reason(), now));
    }

    @EventSourcingHandler
    public void on(PaymentInitiated event) {
        paymentId = event.paymentId();
        merchantId = event.merchantId();
        customerId = event.customerId();
        amount = event.amount();
        paymentMethod = event.paymentMethod();
        idempotencyKey = event.idempotencyKey();
        authorizationExpiresAt = event.authorizationExpiresAt();
        createdAt = event.occurredAt();
        updatedAt = event.occurredAt();
        status = PaymentStatus.INITIATED;
        capturedAmount = Money.zero(amount.currency());
        refundedAmount = Money.zero(amount.currency());
        finalCaptureSucceeded = false;
    }

    @EventSourcingHandler
    public void on(PaymentAuthorized event) {
        authorizedAmount = event.authorizedAmount();
        authorizationCode = event.authorizationCode();
        if (event.authorizationCode() != null) {
            status = PaymentStatus.AUTHORIZED;
        }
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(PaymentCharged event) {
        Capture capture = new Capture(
                event.captureId(),
                event.amount(),
                event.isFinal(),
                event.occurredAt());
        captures.put(event.captureId(), capture);
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(PaymentCompleted event) {
        status = PaymentStatus.COMPLETED;
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(PaymentFailed event) {
        failureReason = event.reason();
        status = PaymentStatus.FAILED;
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(PaymentExpired event) {
        status = PaymentStatus.EXPIRED;
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(RefundRequested event) {
        Refund refund = new Refund(
                event.refundId(),
                event.amount(),
                event.captureId(),
                event.reason(),
                event.idempotencyKey(),
                event.initiatedBy(),
                event.occurredAt());
        refunds.put(event.refundId(), refund);
        idempotencyKeysForRefunds.add(event.idempotencyKey());
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(RefundPending event) {
        Refund refund = refunds.get(event.refundId());
        if (refund != null) {
            refund.markPending();
        }
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(RefundSucceeded event) {
        Refund refund = refunds.get(event.refundId());
        if (refund != null) {
            refund.succeed(event.providerReference(), event.occurredAt());
            refundedAmount = refundedAmount.add(refund.getAmount());
            updatePaymentStatusFromRefund();
        }
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(RefundFailed event) {
        Refund refund = refunds.get(event.refundId());
        if (refund != null) {
            refund.fail(event.reason(), event.occurredAt());
        }
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(CaptureSucceeded event) {
        Capture capture = captures.get(event.captureId());
        if (capture != null) {
            capture.succeed(event.providerReference(), event.occurredAt());
            capturedAmount = capturedAmount.add(capture.getAmount());
            if (capture.isFinal()) {
                finalCaptureSucceeded = true;
            }
            updatePaymentStatusFromCapture();
        }
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(CaptureFailed event) {
        Capture capture = captures.get(event.captureId());
        if (capture != null) {
            capture.fail(event.reason(), event.occurredAt());
        }
        updatedAt = event.occurredAt();
    }

    private void updatePaymentStatusFromCapture() {
        if (capturedAmount.compareTo(authorizedAmount) == 0) {
            status = PaymentStatus.CAPTURED;
        } else if (capturedAmount.isPositive()) {
            status = PaymentStatus.PARTIALLY_CAPTURED;
        }
    }

    private void updatePaymentStatusFromRefund() {
        if (refundedAmount.compareTo(capturedAmount) == 0) {
            status = PaymentStatus.REFUNDED;
        } else if (refundedAmount.isPositive()) {
            status = PaymentStatus.PARTIALLY_REFUNDED;
        }
    }

    private Refund findRefundByIdempotencyKey(String idempotencyKey) {
        for (Refund refund : refunds.values()) {
            if (refund.getIdempotencyKey().equals(idempotencyKey)) {
                return refund;
            }
        }
        return null;
    }

    private boolean canRefund() {
        return status == PaymentStatus.CAPTURED
                || status == PaymentStatus.PARTIALLY_CAPTURED
                || status == PaymentStatus.COMPLETED
                || status == PaymentStatus.PARTIALLY_REFUNDED;
    }

    private boolean isTerminalState(PaymentStatus paymentStatus) {
        return paymentStatus == PaymentStatus.COMPLETED
                || paymentStatus == PaymentStatus.FAILED
                || paymentStatus == PaymentStatus.EXPIRED
                || paymentStatus == PaymentStatus.PARTIALLY_REFUNDED
                || paymentStatus == PaymentStatus.REFUNDED;
    }

    // Getters for testing and query purposes

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public MerchantId getMerchantId() {
        return merchantId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public Money getAmount() {
        return amount;
    }

    public Money getAuthorizedAmount() {
        return authorizedAmount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public Instant getAuthorizationExpiresAt() {
        return authorizationExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Money getCapturedAmount() {
        return capturedAmount;
    }

    public Money getRefundedAmount() {
        return refundedAmount;
    }

    public Map<CaptureId, Capture> getCaptures() {
        return Collections.unmodifiableMap(captures);
    }

    public Map<RefundId, Refund> getRefunds() {
        return Collections.unmodifiableMap(refunds);
    }
}
