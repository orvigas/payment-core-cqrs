package com.orvigas.payment.publish;

import com.orvigas.payment.CaptureFailed;
import com.orvigas.payment.CaptureSucceeded;
import com.orvigas.payment.PaymentAuthorized;
import com.orvigas.payment.PaymentCharged;
import com.orvigas.payment.PaymentCompleted;
import com.orvigas.payment.PaymentExpired;
import com.orvigas.payment.PaymentFailed;
import com.orvigas.payment.PaymentInitiated;
import com.orvigas.payment.RefundFailed;
import com.orvigas.payment.RefundPending;
import com.orvigas.payment.RefundRequested;
import com.orvigas.payment.RefundSucceeded;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Bridges Payment domain events onto Kafka. Registered on its own tracking
 * event processor ({@code payment-kafka-publisher}, configured in
 * {@code application.yml}) rather than the existing subscribing default -
 * see {@code axon-subscribing-vs-tracking-durability.md} for why a
 * subscribing processor doesn't actually guarantee the event was durably
 * committed before this class sees it, and a tracking processor does.
 *
 * <p>Publishing is fire-and-forget from this class's perspective: by the
 * time a tracking processor delivers an event here, the command that raised
 * it has already returned. A Kafka publish failure must never roll back or
 * retrigger that command - there is nothing left to roll back, the event
 * store already has the durable fact. Transient broker issues are absorbed
 * by the producer's own retry/idempotence configuration.
 *
 * <p>The tracking processor's token advances once this method returns,
 * regardless of how the async send eventually resolves - so a publish that
 * survives every producer retry and still fails is not retried by this
 * class. That gap is made observable rather than papered over: every
 * outcome is recorded on the {@code payment.kafka.publish.total} counter
 * and {@code payment.kafka.publish} timer, tagged by {@code eventType},
 * {@code topic}, and {@code outcome}. An alert on
 * {@code outcome="failure"} is the intended way an operator finds out a
 * publish was permanently lost; recovery from there is a manual replay
 * from the event store (still the durable source of truth), not an
 * automatic outbox - see the T-007 handoff log for why that trade-off was
 * made instead of building dead-letter/replay machinery up front.
 *
 * @author orvigas@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ProcessingGroup("payment-kafka-publisher")
public class PaymentEventKafkaPublisher {

    private static final String PUBLISH_TIMER_NAME = "payment.kafka.publish";
    private static final String PUBLISH_COUNTER_NAME = "payment.kafka.publish.total";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Publishes {@link PaymentInitiated} to {@value PaymentKafkaTopics#PAYMENT_INITIATED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(PaymentInitiated event) {
        publish(PaymentInitiatedPayload.from(event));
    }

    /**
     * Publishes {@link PaymentAuthorized} to {@value PaymentKafkaTopics#PAYMENT_CHARGED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(PaymentAuthorized event) {
        publish(PaymentAuthorizedPayload.from(event));
    }

    /**
     * Publishes {@link PaymentCharged} to {@value PaymentKafkaTopics#PAYMENT_CHARGED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(PaymentCharged event) {
        publish(PaymentChargedPayload.from(event));
    }

    /**
     * Publishes {@link CaptureSucceeded} to {@value PaymentKafkaTopics#PAYMENT_CHARGED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(CaptureSucceeded event) {
        publish(CaptureSucceededPayload.from(event));
    }

    /**
     * Publishes {@link CaptureFailed} to {@value PaymentKafkaTopics#PAYMENT_CHARGED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(CaptureFailed event) {
        publish(CaptureFailedPayload.from(event));
    }

    /**
     * Publishes {@link PaymentCompleted} to {@value PaymentKafkaTopics#PAYMENT_COMPLETED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(PaymentCompleted event) {
        publish(PaymentCompletedPayload.from(event));
    }

    /**
     * Publishes {@link PaymentFailed} to {@value PaymentKafkaTopics#PAYMENT_FAILED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(PaymentFailed event) {
        publish(PaymentFailedPayload.from(event));
    }

    /**
     * Publishes {@link PaymentExpired} to {@value PaymentKafkaTopics#PAYMENT_FAILED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(PaymentExpired event) {
        publish(PaymentExpiredPayload.from(event));
    }

    /**
     * Publishes {@link RefundRequested} to {@value PaymentKafkaTopics#PAYMENT_REFUNDED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(RefundRequested event) {
        publish(RefundRequestedPayload.from(event));
    }

    /**
     * Publishes {@link RefundPending} to {@value PaymentKafkaTopics#PAYMENT_REFUNDED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(RefundPending event) {
        publish(RefundPendingPayload.from(event));
    }

    /**
     * Publishes {@link RefundSucceeded} to {@value PaymentKafkaTopics#PAYMENT_REFUNDED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(RefundSucceeded event) {
        publish(RefundSucceededPayload.from(event));
    }

    /**
     * Publishes {@link RefundFailed} to {@value PaymentKafkaTopics#PAYMENT_REFUNDED}.
     *
     * @param event the domain event
     */
    @EventHandler
    public void on(RefundFailed event) {
        publish(RefundFailedPayload.from(event));
    }

    private void publish(PaymentKafkaEvent payload) {
        Timer.Sample sample = Timer.start(meterRegistry);
        kafkaTemplate.send(payload.topic(), payload.paymentId(), payload)
                .whenComplete((result, ex) -> recordOutcome(payload, sample, result, ex));
    }

    private void recordOutcome(PaymentKafkaEvent payload, Timer.Sample sample, SendResult<String, Object> result, Throwable ex) {
        String outcome = ex != null ? "failure" : "success";
        sample.stop(Timer.builder(PUBLISH_TIMER_NAME)
                .description("Time from Kafka send() to producer ack or exhausted retries for a payment event publish")
                .tag("eventType", payload.eventType())
                .tag("topic", payload.topic())
                .tag("outcome", outcome)
                .register(meterRegistry));
        Counter.builder(PUBLISH_COUNTER_NAME)
                .description("Outcome of publishing a payment domain event to Kafka")
                .tag("eventType", payload.eventType())
                .tag("topic", payload.topic())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();

        if (ex != null) {
            log.error(
                    "publish failed for {} payment={} topic={} after producer retries were exhausted",
                    payload.eventType(), payload.paymentId(), payload.topic(), ex);
        } else {
            log.debug(
                    "published {} payment={} topic={} partition={} offset={}",
                    payload.eventType(), payload.paymentId(), payload.topic(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        }
    }
}
