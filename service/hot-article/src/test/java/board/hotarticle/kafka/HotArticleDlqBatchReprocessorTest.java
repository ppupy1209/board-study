package board.hotarticle.kafka;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotArticleDlqBatchReprocessorTest {
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private Acknowledgment acknowledgment;

    private SimpleMeterRegistry meterRegistry;
    private HotArticleDlqBatchReprocessor reprocessor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        reprocessor = new HotArticleDlqBatchReprocessor(
                kafkaTemplate,
                new HotArticleDlqMetrics(meterRegistry),
                3,
                1
        );
    }

    @Test
    void republishesDlqBatchAndAcknowledgesAfterEverySendSucceeds() {
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        reprocessor.replay(List.of(dlqRecord(0)), acknowledgment);

        ArgumentCaptor<ProducerRecord<String, String>> captor = producerRecordCaptor();
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> replayed = captor.getValue();
        assertThat(replayed.topic()).isEqualTo("board-article.hot-article.REPLAY");
        assertThat(replayed.key()).isEqualTo("1");
        assertThat(replayed.value()).isEqualTo("payload");
        assertThat(replayCount(replayed)).isEqualTo(1);
        verify(acknowledgment).acknowledge();
        assertThat(meterRegistry.get("modu.kafka.dlq.replay")
                .tag("topic", "board-article")
                .tag("result", "republished")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void parksRecordAfterReplayLimitInsteadOfDiscardingIt() {
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        reprocessor.replay(List.of(dlqRecord(3)), acknowledgment);

        ArgumentCaptor<ProducerRecord<String, String>> captor = producerRecordCaptor();
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("board-article.hot-article.PARKING");
        assertThat(replayCount(captor.getValue())).isEqualTo(4);
        assertThat(new String(
                captor.getValue().headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                StandardCharsets.UTF_8
        )).isEqualTo("board-article");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void leavesBatchUncommittedWhenRepublishFails() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(failed);

        assertThatThrownBy(() -> reprocessor.replay(List.of(dlqRecord(0)), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to replay DLQ record");

        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<String, String> dlqRecord(int replayCount) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                HotArticleKafkaTopics.BOARD_ARTICLE_DLQ,
                0,
                7L,
                "1",
                "payload"
        );
        record.headers().add(new RecordHeader(
                KafkaHeaders.DLT_ORIGINAL_TOPIC,
                "board-article".getBytes(StandardCharsets.UTF_8)
        ));
        if (replayCount > 0) {
            record.headers().add(new RecordHeader(
                    HotArticleDlqBatchReprocessor.REPLAY_COUNT_HEADER,
                    ByteBuffer.allocate(Integer.BYTES).putInt(replayCount).array()
            ));
        }
        return record;
    }

    private int replayCount(ProducerRecord<String, String> record) {
        return ByteBuffer.wrap(record.headers()
                .lastHeader(HotArticleDlqBatchReprocessor.REPLAY_COUNT_HEADER)
                .value()).getInt();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<ProducerRecord<String, String>> producerRecordCaptor() {
        return (ArgumentCaptor<ProducerRecord<String, String>>) (ArgumentCaptor<?>)
                ArgumentCaptor.forClass(ProducerRecord.class);
    }
}
