package com.orvigas.payment.publish;

/**
 * Kafka topic names for payment domain events. See {@code adr-002} for why
 * these five topics exist rather than either one topic per event type or a
 * strict reuse of the four topics originally named in
 * {@code governance/TECH_STACK.md}: {@code payment-charged} carries every
 * event in the authorize-through-capture phase, {@code payment-failed}
 * carries both provider declines and authorization expiry, and
 * {@code payment-refunded} is a later addition for the refund sub-lifecycle,
 * which doesn't fit any of the other four.
 *
 * @author orvigas@gmail.com
 */
public final class PaymentKafkaTopics {

    /** Carries {@code PaymentInitiated}. */
    public static final String PAYMENT_INITIATED = "payment-initiated";

    /**
     * Carries every event in the authorize-through-capture phase:
     * {@code PaymentAuthorized}, {@code PaymentCharged},
     * {@code CaptureSucceeded}, {@code CaptureFailed}.
     */
    public static final String PAYMENT_CHARGED = "payment-charged";

    /** Carries {@code PaymentCompleted}. */
    public static final String PAYMENT_COMPLETED = "payment-completed";

    /** Carries {@code PaymentFailed} and {@code PaymentExpired}. */
    public static final String PAYMENT_FAILED = "payment-failed";

    /**
     * Carries the refund sub-lifecycle: {@code RefundRequested},
     * {@code RefundPending}, {@code RefundSucceeded}, {@code RefundFailed}.
     */
    public static final String PAYMENT_REFUNDED = "payment-refunded";

    private PaymentKafkaTopics() {
    }
}
