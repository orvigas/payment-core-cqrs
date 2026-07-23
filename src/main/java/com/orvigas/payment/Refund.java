package com.orvigas.payment;

import com.orvigas.shared.id.RefundId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Refund entity inside the Payment aggregate. Represents an attempt to return
 * previously captured funds to the customer.
 *
 * @author orvigas@gmail.com
 */
public class Refund {

    private final RefundId refundId;
    private final Money amount;
    private final RefundReason reason;
    private final String idempotencyKey;
    private RefundStatus status;
    private String providerReference;
    private FailureReason failureReason;
    private Instant requestedAt;
    private Instant resolvedAt;

    /**
     * Creates a new refund in REQUESTED state.
     *
     * @param refundId the refund identifier
     * @param amount the amount being refunded
     * @param reason structured reason plus optional notes
     * @param idempotencyKey client-supplied key for idempotent retries
     * @param requestedAt when the refund was requested
     */
    public Refund(
            RefundId refundId,
            Money amount,
            RefundReason reason,
            String idempotencyKey,
            Instant requestedAt) {
        this.refundId = Objects.requireNonNull(refundId, "refundId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.status = RefundStatus.REQUESTED;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }

    public RefundId getRefundId() {
        return refundId;
    }

    public Money getAmount() {
        return amount;
    }

    public RefundReason getReason() {
        return reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    /**
     * Marks this refund as pending (provider is processing).
     */
    public void markPending() {
        if (status != RefundStatus.REQUESTED) {
            throw new IllegalStateException("refund must be REQUESTED to mark pending, current status: " + status);
        }
        this.status = RefundStatus.PENDING;
    }

    /**
     * Marks this refund as succeeded with the provider's reference.
     *
     * @param providerReference provider's identifier for this refund
     * @param resolvedAt when the provider confirmed
     */
    public void succeed(String providerReference, Instant resolvedAt) {
        if (status != RefundStatus.REQUESTED && status != RefundStatus.PENDING) {
            throw new IllegalStateException(
                    "refund must be REQUESTED or PENDING to succeed, current status: " + status);
        }
        this.providerReference = Objects.requireNonNull(providerReference, "providerReference must not be null");
        this.status = RefundStatus.SUCCEEDED;
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
    }

    /**
     * Marks this refund as failed with the provider's reason.
     *
     * @param failureReason provider's decline reason
     * @param resolvedAt when the provider declined
     */
    public void fail(FailureReason failureReason, Instant resolvedAt) {
        if (status == RefundStatus.SUCCEEDED) {
            throw new IllegalStateException("refund must not be SUCCEEDED to fail, current status: " + status);
        }
        this.failureReason = Objects.requireNonNull(failureReason, "failureReason must not be null");
        this.status = RefundStatus.FAILED;
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
    }

    /**
     * Reports whether this refund succeeded.
     *
     * @return {@code true} if status is SUCCEEDED
     */
    public boolean isSucceeded() {
        return status == RefundStatus.SUCCEEDED;
    }
}
