package com.orvigas.support.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code spring-kafka-test} embedded broker slice actually works,
 * independent of the application's own Kafka wiring - there are no
 * {@code @KafkaListener} beans or topics yet for a full context-level test to
 * exercise. A minimal producer/consumer round trip against the embedded
 * broker is what later tasks (payment event topics) will build their own
 * consumer tests on top of.
 *
 * <p>This deliberately does not load the Spring application context: the
 * broker is embedded in this JVM regardless, and coupling it to
 * {@code AbstractIntegrationTest} would mean paying for Postgres and MongoDB
 * containers just to prove Kafka wiring, which is unrelated infrastructure.
 * {@code @EmbeddedKafka} carries its own JUnit 5 extension
 * ({@code EmbeddedKafkaCondition}), which resolves {@link EmbeddedKafkaBroker}
 * lifecycle-method parameters directly - no Spring context needed for that
 * part either.
 *
 * @author orvigas@gmail.com
 */
@EmbeddedKafka(partitions = 1, topics = "payment-core-harness-test")
class EmbeddedKafkaRoundTripTest {

    private static final String TOPIC = "payment-core-harness-test";
    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(10);

    private EmbeddedKafkaBroker broker;
    private KafkaMessageListenerContainer<String, String> listenerContainer;
    private DefaultKafkaProducerFactory<String, String> producerFactory;
    private BlockingQueue<ConsumerRecord<String, String>> received;

    @BeforeEach
    void startConsumer(EmbeddedKafkaBroker embeddedKafkaBroker) {
        broker = embeddedKafkaBroker;
        received = new LinkedBlockingQueue<>();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("harness-test-group", "true", broker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProps);
        var containerProperties = new ContainerProperties(TOPIC);
        listenerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        listenerContainer.setupMessageListener((MessageListener<String, String>) received::add);
        listenerContainer.start();

        // The listener container's rebalance is asynchronous; without this the
        // producer below can send before a partition is assigned and the
        // record is missed rather than just delayed.
        ContainerTestUtils.waitForAssignment(listenerContainer, 1);
    }

    @AfterEach
    void stopConsumer() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
        // Left running, the producer's background client keeps retrying
        // against the now-stopped broker and logs connection-refused
        // warnings that bleed into whichever test class runs next.
        if (producerFactory != null) {
            producerFactory.destroy();
        }
    }

    @Test
    void producedRecordIsConsumedFromEmbeddedBroker() throws InterruptedException {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        var template = new KafkaTemplate<>(producerFactory);

        template.send(TOPIC, "harness-key", "harness-payload");

        ConsumerRecord<String, String> record = received.poll(RECEIVE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertThat(record).as("record should arrive within %s", RECEIVE_TIMEOUT).isNotNull();
        assertThat(record.key()).isEqualTo("harness-key");
        assertThat(record.value()).isEqualTo("harness-payload");
    }
}
