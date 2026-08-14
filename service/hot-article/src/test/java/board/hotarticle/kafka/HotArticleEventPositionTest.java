package board.hotarticle.kafka;

import board.common.event.payload.EventType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotArticleEventPositionTest {
    @Test
    void usesCurrentPositionForOriginalRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                EventType.Topic.BOARD_VIEW,
                2,
                17L,
                "1",
                "payload"
        );

        assertThat(HotArticleEventPosition.from(record))
                .isEqualTo(new HotArticleEventPosition(EventType.Topic.BOARD_VIEW, 2, 17L));
    }

    @Test
    void keepsOriginalPositionWhenRecordIsReplayed() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                HotArticleKafkaTopics.BOARD_VIEW_REPLAY,
                0,
                99L,
                "1",
                "payload"
        );
        new HotArticleEventPosition(EventType.Topic.BOARD_VIEW, 2, 17L).addTo(record.headers());

        assertThat(HotArticleEventPosition.from(record))
                .isEqualTo(new HotArticleEventPosition(EventType.Topic.BOARD_VIEW, 2, 17L));
    }

    @Test
    void derivesOriginalPositionFromFirstDeadLetterHeaders() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                HotArticleKafkaTopics.BOARD_VIEW_DLQ,
                0,
                5L,
                "1",
                "payload"
        );
        record.headers().add(new RecordHeader(
                KafkaHeaders.DLT_ORIGINAL_TOPIC,
                EventType.Topic.BOARD_VIEW.getBytes(StandardCharsets.UTF_8)
        ));
        record.headers().add(new RecordHeader(
                KafkaHeaders.DLT_ORIGINAL_PARTITION,
                ByteBuffer.allocate(Integer.BYTES).putInt(2).array()
        ));
        record.headers().add(new RecordHeader(
                KafkaHeaders.DLT_ORIGINAL_OFFSET,
                ByteBuffer.allocate(Long.BYTES).putLong(17L).array()
        ));

        assertThat(HotArticleEventPosition.fromDeadLetter(record))
                .isEqualTo(new HotArticleEventPosition(EventType.Topic.BOARD_VIEW, 2, 17L));
    }

    @Test
    void rejectsReplayRecordWithoutOriginalPosition() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                HotArticleKafkaTopics.BOARD_VIEW_REPLAY,
                0,
                99L,
                "1",
                "payload"
        );

        assertThatThrownBy(() -> HotArticleEventPosition.from(record))
                .isInstanceOf(InvalidHotArticleEventException.class)
                .hasMessageContaining(HotArticleEventPosition.ORIGINAL_TOPIC_HEADER);
    }
}
