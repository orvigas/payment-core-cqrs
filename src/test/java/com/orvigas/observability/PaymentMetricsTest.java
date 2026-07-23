package com.orvigas.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentMetrics}.
 *
 * <p>Uses a {@link SimpleMeterRegistry} so no Prometheus or Actuator infrastructure
 * is needed.
 *
 * @author orvigas@gmail.com
 */
class PaymentMetricsTest {

    private MeterRegistry meterRegistry;
    private PaymentMetrics paymentMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        paymentMetrics = new PaymentMetrics(meterRegistry);
    }

    @Test
    void countersStartAtZero() {
        assertThat(meterRegistry.counter("payment.initiated").count()).isZero();
        assertThat(meterRegistry.counter("payment.captured").count()).isZero();
        assertThat(meterRegistry.counter("payment.refunded").count()).isZero();
    }

    @Test
    void initiatedCounterIncrements() {
        paymentMetrics.recordPaymentInitiated();
        assertThat(meterRegistry.counter("payment.initiated").count()).isEqualTo(1.0);
    }

    @Test
    void capturedCounterIncrements() {
        paymentMetrics.recordPaymentCaptured();
        assertThat(meterRegistry.counter("payment.captured").count()).isEqualTo(1.0);
    }

    @Test
    void refundedCounterIncrements() {
        paymentMetrics.recordPaymentRefunded();
        assertThat(meterRegistry.counter("payment.refunded").count()).isEqualTo(1.0);
    }

    @Test
    void activeGaugeIncrementsOnInit() {
        paymentMetrics.recordPaymentInitiated();
        var gauge = meterRegistry.find("payment.active").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(1.0);
    }

    @Test
    void activeGaugeDecrementsOnCompletion() {
        paymentMetrics.recordPaymentInitiated();
        paymentMetrics.recordPaymentCompleted();
        var gauge = meterRegistry.find("payment.active").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isZero();
    }

    @Test
    void timerRecordsLatency() {
        var sample = paymentMetrics.startTimer();
        paymentMetrics.stopTimer(sample);
        var timer = meterRegistry.find("payment.latency").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isOne();
    }
}
