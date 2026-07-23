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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
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
 * by the producer's own retry/idempotence configuration; a failure that
 * survives every producer retry is logged for manual follow-up rather than
 * retried here (see the T-007 handoff log for why a full outbox table isn't
 * warranted yet).
 *
 * @author orvigas@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ProcessingGroup("payment-kafka-publisher")
public class PaymentEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

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
        kafkaTemplate.send(payload.topic(), payload.paymentId(), payload)
                .whenComplete((result, ex) -> {
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
                });
    }
}
