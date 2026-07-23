package com.orvigas.payment.publish;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common contract for the immutable, versioned payloads published to Kafka
 * for payment domain events. These are deliberately distinct types from the
 * Axon domain events in {@code com.orvigas.payment}: a Kafka payload is a
 * wire contract with its own evolution story (schema version, explicit
 * discriminator), while the domain event is an internal implementation
 * detail of the aggregate. Nothing outside this package should ever
 * serialize an Axon {@code EventMessage} or a domain event record directly
 * onto a topic.
 *
 * @author orvigas@gmail.com
 */
public sealed interface PaymentKafkaEvent permits
        PaymentInitiatedPayload,
        PaymentAuthorizedPayload,
        PaymentChargedPayload,
        CaptureSucceededPayload,
        CaptureFailedPayload,
        PaymentCompletedPayload,
        PaymentFailedPayload,
        PaymentExpiredPayload,
        RefundRequestedPayload,
        RefundPendingPayload,
        RefundSucceededPayload,
        RefundFailedPayload {

    /**
     * The Kafka topic this payload belongs on. See {@code adr-002} for the
     * mapping from domain event type to topic.
     *
     * @return the destination topic name
     */
    String topic();

    /**
     * The payment aggregate identifier, used as the Kafka partition key so
     * that a single payment's events stay in order within a topic.
     *
     * @return the payment identifier as a string
     */
    String paymentId();

    /**
     * Discriminator identifying which event this payload represents, for
     * topics that carry more than one event type.
     *
     * <p>{@code @JsonProperty} is required here, not optional polish: none of
     * these three methods are canonical record components, and Jackson only
     * auto-detects bean-style {@code getXxx()}/{@code isXxx()} accessors -
     * without the explicit annotation, the field silently disappears from
     * the wire payload instead of failing loudly, which is exactly what
     * happened the first time this shipped (see the T-007 handoff log).
     *
     * @return the event type name
     */
    @JsonProperty("eventType")
    String eventType();

    /**
     * Payload schema revision. Bump when a field is added, removed, or
     * retyped in a way that isn't purely additive-and-optional.
     *
     * @return the schema version
     */
    @JsonProperty("schemaVersion")
    int schemaVersion();
}
