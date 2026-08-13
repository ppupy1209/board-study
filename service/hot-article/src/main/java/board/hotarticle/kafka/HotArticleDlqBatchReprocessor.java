package board.hotarticle.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class HotArticleDlqBatchReprocessor {
    static final String REPLAY_COUNT_HEADER = "x-modu-dlq-replay-count";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final HotArticleDlqMetrics metrics;
    private final int maxReplayAttempts;
    private final Duration sendTimeout;

    public HotArticleDlqBatchReprocessor(
            KafkaTemplate<String, String> kafkaTemplate,
            HotArticleDlqMetrics metrics,
            @Value("${modu.kafka.failure.max-replay-attempts:3}") int maxReplayAttempts,
            @Value("${modu.kafka.failure.replay-send-timeout-seconds:5}") long sendTimeoutSeconds
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
        this.maxReplayAttempts = maxReplayAttempts;
        this.sendTimeout = Duration.ofSeconds(sendTimeoutSeconds);
    }

    @KafkaListener(
            id = "hotArticleDlqBatchReprocessor",
            topics = {
                    HotArticleKafkaTopics.BOARD_ARTICLE_DLQ,
                    HotArticleKafkaTopics.BOARD_COMMENT_DLQ,
                    HotArticleKafkaTopics.BOARD_LIKE_DLQ,
                    HotArticleKafkaTopics.BOARD_VIEW_DLQ
            },
            groupId = HotArticleKafkaTopics.DLQ_REPLAY_GROUP,
            containerFactory = "dlqBatchKafkaListenerContainerFactory"
    )
    public void replay(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        metrics.recordReplayBatch(records.size());
        for (ConsumerRecord<String, String> record : records) {
            replay(record);
        }
        acknowledgment.acknowledge();
    }

    private void replay(ConsumerRecord<String, String> record) {
        String originalTopic = originalTopic(record);
        int replayCount = replayCount(record);
        boolean park = replayCount >= maxReplayAttempts;
        String destination = park
                ? HotArticleKafkaTopics.parkingTopic(originalTopic)
                : HotArticleKafkaTopics.replayTopic(originalTopic);

        ProducerRecord<String, String> output = new ProducerRecord<>(destination, record.key(), record.value());
        if (park) {
            record.headers().forEach(header -> {
                if (!REPLAY_COUNT_HEADER.equals(header.key())) {
                    output.headers().add(new RecordHeader(header.key(), header.value()));
                }
            });
        }
        output.headers().add(new RecordHeader(
                REPLAY_COUNT_HEADER,
                ByteBuffer.allocate(Integer.BYTES).putInt(replayCount + 1).array()
        ));

        try {
            kafkaTemplate.send(output).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            metrics.recordReplay(originalTopic, park ? "parked" : "republished");
            log.info(
                    "[HotArticleDlqBatchReprocessor.replay] originalTopic={}, dlqTopic={}, offset={}, replayCount={}, destination={}",
                    originalTopic,
                    record.topic(),
                    record.offset(),
                    replayCount,
                    destination
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.recordReplay(originalTopic, "failed");
            throw new IllegalStateException("Interrupted while replaying DLQ record", e);
        } catch (Exception e) {
            metrics.recordReplay(originalTopic, "failed");
            throw new IllegalStateException("Failed to replay DLQ record", e);
        }
    }

    private String originalTopic(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC);
        if (header == null) {
            throw new IllegalArgumentException("DLQ record has no original topic header");
        }
        return HotArticleKafkaTopics.originalTopic(new String(header.value(), StandardCharsets.UTF_8));
    }

    private int replayCount(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(REPLAY_COUNT_HEADER);
        if (header == null) {
            return 0;
        }
        if (header.value().length != Integer.BYTES) {
            throw new IllegalArgumentException("Invalid DLQ replay count header");
        }
        return ByteBuffer.wrap(header.value()).getInt();
    }
}
