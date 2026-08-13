package board.hotarticle.kafka;

import board.common.event.payload.EventType;
import board.hotarticle.config.KafkaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = HotArticleKafkaFailureIntegrationTest.TestApplication.class,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=modu-square-hot-article-service",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.enable-auto-commit=false",
                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "modu.kafka.failure.retry-interval-ms=10",
                "modu.kafka.failure.max-attempts=3"
        }
)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                HotArticleKafkaFailureIntegrationTest.INPUT_TOPIC,
                HotArticleKafkaFailureIntegrationTest.DLQ_TOPIC
        }
)
@DirtiesContext
class HotArticleKafkaFailureIntegrationTest {
    static final String INPUT_TOPIC = EventType.Topic.BOARD_VIEW;
    static final String DLQ_TOPIC = INPUT_TOPIC + HotArticleKafkaTopics.DLQ_SUFFIX;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;
    @Autowired
    private MeterRegistry meterRegistry;

    private Consumer<String, String> dlqConsumer;

    @BeforeEach
    void setUp() {
        FailingListener.ATTEMPTS.set(0);
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                UUID.randomUUID().toString(),
                "false",
                embeddedKafka
        );
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dlqConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(dlqConsumer, DLQ_TOPIC);
    }

    @AfterEach
    void tearDown() {
        dlqConsumer.close();
    }

    @Test
    void retriesThreeTimesThenPublishesFailedRecordToDlq() {
        kafkaTemplate.send(INPUT_TOPIC, "article-1", "poison-event").join();

        ConsumerRecord<String, String> dlqRecord = KafkaTestUtils.getSingleRecord(
                dlqConsumer,
                DLQ_TOPIC,
                Duration.ofSeconds(10)
        );

        assertThat(FailingListener.ATTEMPTS).hasValue(3);
        assertThat(dlqRecord.key()).isEqualTo("article-1");
        assertThat(dlqRecord.value()).isEqualTo("poison-event");
        assertThat(new String(
                dlqRecord.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                StandardCharsets.UTF_8
        )).isEqualTo(INPUT_TOPIC);
        assertThat(meterRegistry.get("modu.kafka.delivery")
                .tag("topic", INPUT_TOPIC)
                .tag("result", "retry")
                .counter().count()).isEqualTo(2);
        assertThat(meterRegistry.get("modu.kafka.delivery")
                .tag("topic", INPUT_TOPIC)
                .tag("result", "dlq")
                .counter().count()).isEqualTo(1);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({KafkaConfig.class, FailingListener.class})
    static class TestApplication {
    }

    static class FailingListener {
        static final AtomicInteger ATTEMPTS = new AtomicInteger();

        @KafkaListener(
                id = "hotArticleFailureIntegrationListener",
                topics = INPUT_TOPIC,
                groupId = "hot-article-failure-it-group",
                containerFactory = "kafkaListenerContainerFactory"
        )
        void listen(String message, Acknowledgment acknowledgment) {
            ATTEMPTS.incrementAndGet();
            throw new IllegalStateException("forced failure");
        }
    }
}
