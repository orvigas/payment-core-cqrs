package com.orvigas.payment;

import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Capture entity inside the Payment aggregate. Represents an attempt to claim
 * previously authorized funds.
 *
 * @author orvigas@gmail.com
 */
public class Capture {

    private final CaptureId captureId;
    private final Money amount;
    private final boolean isFinal;
    private CaptureStatus status;
    private String providerReference;
    private FailureReason failureReason;
    private Instant requestedAt;
    private Instant resolvedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Capture capture)) {
            return false;
        }
        return isFinal == capture.isFinal
                && Objects.equals(captureId, capture.captureId)
                && Objects.equals(amount, capture.amount)
                && status == capture.status
                && Objects.equals(providerReference, capture.providerReference)
                && Objects.equals(failureReason, capture.failureReason)
                && Objects.equals(requestedAt, capture.requestedAt)
                && Objects.equals(resolvedAt, capture.resolvedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(captureId, amount, isFinal, status, providerReference, failureReason, requestedAt, resolvedAt);
    }

    /**
     * Creates a new capture in PENDING state.
     *
     * @param captureId the capture identifier
     * @param amount the amount being captured
     * @param isFinal whether this is the final capture
     * @param requestedAt when the capture was requested
     */
    public Capture(CaptureId captureId, Money amount, boolean isFinal, Instant requestedAt) {
        this.captureId = Objects.requireNonNull(captureId, "captureId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.isFinal = isFinal;
        this.status = CaptureStatus.PENDING;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }

    public CaptureId getCaptureId() {
        return captureId;
    }

    public Money getAmount() {
        return amount;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public CaptureStatus getStatus() {
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
     * Marks this capture as succeeded with the provider's reference.
     *
     * @param providerReference provider's identifier for this capture
     * @param resolvedAt when the provider confirmed
     */
    public void succeed(String providerReference, Instant resolvedAt) {
        if (status != CaptureStatus.PENDING) {
            throw new IllegalStateException("capture must be PENDING to succeed, current status: " + status);
        }
        this.providerReference = Objects.requireNonNull(providerReference, "providerReference must not be null");
        this.status = CaptureStatus.SUCCEEDED;
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
    }

    /**
     * Marks this capture as failed with the provider's reason.
     *
     * @param failureReason provider's decline reason
     * @param resolvedAt when the provider declined
     */
    public void fail(FailureReason failureReason, Instant resolvedAt) {
        if (status != CaptureStatus.PENDING) {
            throw new IllegalStateException("capture must be PENDING to fail, current status: " + status);
        }
        this.failureReason = Objects.requireNonNull(failureReason, "failureReason must not be null");
        this.status = CaptureStatus.FAILED;
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
    }

    /**
     * Reports whether this capture succeeded.
     *
     * @return {@code true} if status is SUCCEEDED
     */
    public boolean isSucceeded() {
        return status == CaptureStatus.SUCCEEDED;
    }
}
