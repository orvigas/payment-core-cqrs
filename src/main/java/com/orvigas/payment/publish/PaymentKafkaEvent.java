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
 * <p>Consumers (T-005's read projection, and any future one) must be
 * idempotent against redelivery of the same payload: a tracking-processor
 * crash between a successful Kafka send and the token store commit will
 * redeliver the event on restart, so "already applied this exact payload"
 * has to be a safe no-op rather than an assumption that delivery happens
 * exactly once.
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
     * <p>Deliberately not {@code @JsonProperty}-annotated: this is routing
     * metadata the publisher uses to pick a destination, not part of the
     * wire payload - a consumer already knows the topic a record arrived on
     * without needing it repeated inside the record's own body.
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
     * <p>Unlike {@link #topic()}, this one has to reach the wire, so it
     * carries {@code @JsonProperty}. That annotation is required, not
     * optional polish: none of the three methods on this interface are
     * canonical record components, and Jackson only auto-detects bean-style
     * {@code getXxx()}/{@code isXxx()} accessors - without it, the field
     * silently disappears from the payload instead of failing loudly, which
     * is exactly what happened the first time this shipped (see the T-007
     * handoff log).
     *
     * @return the event type name
     */
    @JsonProperty("eventType")
    String eventType();

    /**
     * Payload schema revision. Bump when a field is added, removed, or
     * retyped in a way that isn't purely additive-and-optional. Carries
     * {@code @JsonProperty} for the same reason {@link #eventType()} does.
     *
     * @return the schema version
     */
    @JsonProperty("schemaVersion")
    int schemaVersion();
}
