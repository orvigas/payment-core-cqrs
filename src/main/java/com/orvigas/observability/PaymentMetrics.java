package com.orvigas.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Application-level payment metrics exposed via Micrometer and scraped by Prometheus.
 *
 * <p>Provides counters for payment lifecycle stages, a gauge for in-flight
 * payments, and a latency timer for payment processing.
 *
 * @author orvigas@gmail.com
 */
@Component
public class PaymentMetrics {

    private final Counter paymentInitiatedCounter;
    private final Counter paymentCapturedCounter;
    private final Counter paymentRefundedCounter;
    private final Timer paymentLatency;
    private final AtomicInteger activePayments;

    /**
     * Constructs the metrics and registers them with the given registry.
     *
     * @param meterRegistry the Micrometer registry
     */
    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.paymentInitiatedCounter = Counter.builder("payment.initiated")
                .description("Total number of payment initiations")
                .register(meterRegistry);
        this.paymentCapturedCounter = Counter.builder("payment.captured")
                .description("Total number of payment captures")
                .register(meterRegistry);
        this.paymentRefundedCounter = Counter.builder("payment.refunded")
                .description("Total number of payment refunds")
                .register(meterRegistry);
        this.paymentLatency = Timer.builder("payment.latency")
                .description("Payment processing latency in milliseconds")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.activePayments = new AtomicInteger(0);
        Gauge.builder("payment.active", activePayments, AtomicInteger::get)
                .description("Currently active (in-flight) payments")
                .register(meterRegistry);
    }

    /** Increments the payment-initiated counter and the active gauge. */
    public void recordPaymentInitiated() {
        paymentInitiatedCounter.increment();
        activePayments.incrementAndGet();
    }

    /** Increments the payment-captured counter. */
    public void recordPaymentCaptured() {
        paymentCapturedCounter.increment();
    }

    /** Increments the payment-refunded counter. */
    public void recordPaymentRefunded() {
        paymentRefundedCounter.increment();
    }

    /** Decrements the active-payment gauge when a payment completes or fails. */
    public void recordPaymentCompleted() {
        activePayments.decrementAndGet();
    }

    /**
     * Starts a latency sample for a payment operation.
     *
     * @return a new timer sample
     */
    public Timer.Sample startTimer() {
        return Timer.start();
    }

    /**
     * Stops and records the given timer sample against the payment latency histogram.
     *
     * @param sample the timer sample to stop
     */
    public void stopTimer(Timer.Sample sample) {
        sample.stop(paymentLatency);
    }
}
