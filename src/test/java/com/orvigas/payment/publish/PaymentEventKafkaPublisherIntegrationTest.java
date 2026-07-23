package com.orvigas.payment.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orvigas.payment.AuthorizePaymentCommand;
import com.orvigas.payment.CapturePaymentCommand;
import com.orvigas.payment.CompletePaymentCommand;
import com.orvigas.payment.ConfirmCaptureCommand;
import com.orvigas.payment.FailPaymentCommand;
import com.orvigas.payment.FailureReason;
import com.orvigas.payment.InitiatePaymentCommand;
import com.orvigas.payment.PaymentMethod;
import com.orvigas.payment.RefundInitiator;
import com.orvigas.payment.RefundInitiatorType;
import com.orvigas.payment.RefundPaymentCommand;
import com.orvigas.payment.RefundReason;
import com.orvigas.payment.RefundReasonCode;
import com.orvigas.shared.id.CaptureId;
import com.orvigas.shared.id.CustomerId;
import com.orvigas.shared.id.MerchantId;
import com.orvigas.shared.id.PaymentId;
import com.orvigas.shared.money.Money;
import com.orvigas.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves a committed command actually results in the correct Kafka topic
 * receiving the correct payload, end to end: real commands go through the
 * real {@link CommandGateway}, are event-sourced into the real (Testcontainers)
 * MongoDB event store, and {@link PaymentEventKafkaPublisher}'s tracking
 * processor picks the resulting events up from there and publishes them to
 * an embedded broker.
 *
 * <p>Builds on the consumer/producer idiom proven in
 * {@code EmbeddedKafkaRoundTripTest}, but - unlike that harness-only test -
 * this one deliberately loads the full Spring context: the whole point is to
 * prove the Axon-to-Kafka bridge, which only exists once Axon, the real
 * Mongo event store, and the publisher's tracking processor are wired
 * together.
 *
 * @author orvigas@gmail.com
 */
@RequiredArgsConstructor
// ApplicationContextLoadTest gets away without this because ApplicationContext
// is one of a handful of types Spring always resolves for a test constructor
// regardless of autowire mode. CommandGateway is an ordinary bean, so it needs
// the test context's constructor autowiring explicitly turned on.
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EmbeddedKafka(partitions = 1, topics = {
        PaymentKafkaTopics.PAYMENT_INITIATED,
        PaymentKafkaTopics.PAYMENT_CHARGED,
        PaymentKafkaTopics.PAYMENT_COMPLETED,
        PaymentKafkaTopics.PAYMENT_FAILED,
        PaymentKafkaTopics.PAYMENT_REFUNDED
})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class PaymentEventKafkaPublisherIntegrationTest extends AbstractIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

    private final CommandGateway commandGateway;

    // Autowired rather than resolved as a @BeforeEach parameter: with a Spring
    // context in play, EmbeddedKafkaCondition's own parameter resolution
    // clashes with SpringExtension's, so the broker has to come through
    // ordinary DI instead - see the spring-kafka issue referenced in the
    // T-007 handoff log.
    private final EmbeddedKafkaBroker broker;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<ConsumerRecord<String, String>> received = new LinkedBlockingQueue<>();
    private KafkaMessageListenerContainer<String, String> listenerContainer;

    @BeforeEach
    void startConsumer() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("payment-publisher-test-group", "true", broker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProps);
        var containerProperties = new ContainerProperties(
                PaymentKafkaTopics.PAYMENT_INITIATED,
                PaymentKafkaTopics.PAYMENT_CHARGED,
                PaymentKafkaTopics.PAYMENT_COMPLETED,
                PaymentKafkaTopics.PAYMENT_FAILED,
                PaymentKafkaTopics.PAYMENT_REFUNDED);
        listenerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        listenerContainer.setupMessageListener((MessageListener<String, String>) received::add);
        listenerContainer.start();
        ContainerTestUtils.waitForAssignment(listenerContainer, 5);
    }

    @AfterEach
    void stopConsumer() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    /**
     * Polls the shared queue until a record matching the given topic, key,
     * and {@code eventType} discriminator turns up, discarding anything else
     * seen along the way. Safe because each test drives the aggregate
     * through its events in the same order they're published, so an
     * intervening record is always one this call doesn't need back.
     */
    private Map<String, Object> awaitRecord(String topic, String paymentId, String eventType) {
        Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecord<String, String> record;
            try {
                record = received.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for a Kafka record", e);
            }
            if (record == null || !record.topic().equals(topic) || !record.key().equals(paymentId)) {
                continue;
            }
            Map<String, Object> value = readValue(record);
            if (eventType.equals(value.get("eventType"))) {
                return value;
            }
        }
        throw new AssertionError(
                "no " + eventType + " record observed on topic " + topic + " for payment " + paymentId
                        + " within " + AWAIT_TIMEOUT);
    }

    // ObjectMapper.readValue(String, Class) with a raw Map.class is inherently
    // an unchecked cast to Map<String, Object>; there's no parameterized
    // target type here since the payload shape varies per event.
    @SuppressWarnings("unchecked")
    private Map<String, Object> readValue(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(record.value(), Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not parse Kafka payload as JSON: " + record.value(), e);
        }
    }

    private PaymentId initiatePayment(Money amount) {
        PaymentId paymentId = PaymentId.newId();
        commandGateway.sendAndWait(new InitiatePaymentCommand(
                paymentId,
                MerchantId.newId(),
                CustomerId.newId(),
                amount,
                new PaymentMethod("tok_integration_test"),
                "idem-" + paymentId.value(),
                Instant.now().plusSeconds(3600)));
        return paymentId;
    }

    @Test
    @DisplayName("initiate, authorize, capture, confirm and complete each publish to their mapped topic")
    void testFullSuccessFlowPublishesToEachMappedTopic() {
        Money amount = Money.of(5000, "USD");
        PaymentId paymentId = initiatePayment(amount);

        Map<String, Object> initiated = awaitRecord(PaymentKafkaTopics.PAYMENT_INITIATED, paymentId.value().toString(), "PaymentInitiated");
        assertThat(initiated.get("currencyCode")).isEqualTo("USD");
        assertThat(((Number) initiated.get("amountMinorUnits")).longValue()).isEqualTo(5000L);

        commandGateway.sendAndWait(new AuthorizePaymentCommand(paymentId, amount, "auth-code-1", null));
        awaitRecord(PaymentKafkaTopics.PAYMENT_CHARGED, paymentId.value().toString(), "PaymentAuthorized");

        commandGateway.sendAndWait(new CapturePaymentCommand(paymentId, amount, true));
        Map<String, Object> charged = awaitRecord(PaymentKafkaTopics.PAYMENT_CHARGED, paymentId.value().toString(), "PaymentCharged");
        CaptureId captureId = CaptureId.fromString((String) charged.get("captureId"));

        commandGateway.sendAndWait(new ConfirmCaptureCommand(paymentId, captureId, "provider-capture-ref"));
        awaitRecord(PaymentKafkaTopics.PAYMENT_CHARGED, paymentId.value().toString(), "CaptureSucceeded");

        commandGateway.sendAndWait(new CompletePaymentCommand(paymentId));
        Map<String, Object> completed = awaitRecord(PaymentKafkaTopics.PAYMENT_COMPLETED, paymentId.value().toString(), "PaymentCompleted");
        assertThat(completed.get("paymentId")).isEqualTo(paymentId.value().toString());
    }

    @Test
    @DisplayName("a failed payment publishes to payment-failed, not payment-completed")
    void testFailedPaymentPublishesToFailedTopic() {
        PaymentId paymentId = initiatePayment(Money.of(2000, "USD"));

        commandGateway.sendAndWait(
                new FailPaymentCommand(paymentId, new FailureReason("card_declined", "issuer declined the card")));

        Map<String, Object> failed = awaitRecord(PaymentKafkaTopics.PAYMENT_FAILED, paymentId.value().toString(), "PaymentFailed");
        assertThat(failed.get("failureCode")).isEqualTo("card_declined");

        boolean nothingOnCompletedTopic = received.stream()
                .noneMatch(r -> r.topic().equals(PaymentKafkaTopics.PAYMENT_COMPLETED)
                        && r.key().equals(paymentId.value().toString()));
        assertThat(nothingOnCompletedTopic).isTrue();
    }

    @Test
    @DisplayName("a requested refund after completion publishes to the new payment-refunded topic")
    void testRefundRequestPublishesToRefundedTopic() {
        Money amount = Money.of(3000, "USD");
        PaymentId paymentId = initiatePayment(amount);
        commandGateway.sendAndWait(new AuthorizePaymentCommand(paymentId, amount, "auth-code-2", null));
        commandGateway.sendAndWait(new CapturePaymentCommand(paymentId, amount, true));
        Map<String, Object> charged = awaitRecord(PaymentKafkaTopics.PAYMENT_CHARGED, paymentId.value().toString(), "PaymentCharged");
        CaptureId captureId = CaptureId.fromString((String) charged.get("captureId"));
        commandGateway.sendAndWait(new ConfirmCaptureCommand(paymentId, captureId, "provider-capture-ref-2"));
        commandGateway.sendAndWait(new CompletePaymentCommand(paymentId));
        awaitRecord(PaymentKafkaTopics.PAYMENT_COMPLETED, paymentId.value().toString(), "PaymentCompleted");

        commandGateway.sendAndWait(new RefundPaymentCommand(
                paymentId,
                amount,
                captureId,
                RefundReason.of(RefundReasonCode.REQUESTED_BY_CUSTOMER),
                "refund-idem-" + paymentId.value(),
                new RefundInitiator(RefundInitiatorType.MERCHANT_USER, "merchant-user-42")));

        Map<String, Object> refundRequested = awaitRecord(
                PaymentKafkaTopics.PAYMENT_REFUNDED, paymentId.value().toString(), "RefundRequested");
        assertThat(refundRequested.get("reasonCode")).isEqualTo("REQUESTED_BY_CUSTOMER");
        assertThat(((Number) refundRequested.get("amountMinorUnits")).longValue()).isEqualTo(3000L);
    }
}
