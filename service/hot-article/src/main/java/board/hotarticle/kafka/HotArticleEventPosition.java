package board.hotarticle.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record HotArticleEventPosition(String topic, int partition, long offset) {
    static final String ORIGINAL_TOPIC_HEADER = "x-modu-original-topic";
    static final String ORIGINAL_PARTITION_HEADER = "x-modu-original-partition";
    static final String ORIGINAL_OFFSET_HEADER = "x-modu-original-offset";

    public HotArticleEventPosition {
        HotArticleKafkaTopics.requireOriginalTopic(topic);
        if (partition < 0) {
            throw new IllegalArgumentException("Partition must not be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative");
        }
    }

    public static HotArticleEventPosition from(ConsumerRecord<String, String> record) {
        String originalTopic = HotArticleKafkaTopics.originalTopic(record.topic());
        if (!record.topic().endsWith(HotArticleKafkaTopics.REPLAY_SUFFIX)) {
            return new HotArticleEventPosition(originalTopic, record.partition(), record.offset());
        }

        return fromHeaders(record.headers(), "REPLAY record");
    }

    static HotArticleEventPosition fromDeadLetter(ConsumerRecord<String, String> record) {
        if (hasOriginalPosition(record.headers())) {
            return fromHeaders(record.headers(), "DLQ record");
        }

        return new HotArticleEventPosition(
                HotArticleKafkaTopics.originalTopic(readString(
                        record.headers(), KafkaHeaders.DLT_ORIGINAL_TOPIC, "DLQ record"
                )),
                readInt(record.headers(), KafkaHeaders.DLT_ORIGINAL_PARTITION, "DLQ record"),
                readLong(record.headers(), KafkaHeaders.DLT_ORIGINAL_OFFSET, "DLQ record")
        );
    }

    void addTo(Headers headers) {
        headers.add(new RecordHeader(ORIGINAL_TOPIC_HEADER, topic.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(
                ORIGINAL_PARTITION_HEADER,
                ByteBuffer.allocate(Integer.BYTES).putInt(partition).array()
        ));
        headers.add(new RecordHeader(
                ORIGINAL_OFFSET_HEADER,
                ByteBuffer.allocate(Long.BYTES).putLong(offset).array()
        ));
    }

    static boolean isPositionHeader(String headerName) {
        return ORIGINAL_TOPIC_HEADER.equals(headerName)
                || ORIGINAL_PARTITION_HEADER.equals(headerName)
                || ORIGINAL_OFFSET_HEADER.equals(headerName);
    }

    private static HotArticleEventPosition fromHeaders(Headers headers, String source) {
        return new HotArticleEventPosition(
                HotArticleKafkaTopics.originalTopic(readString(headers, ORIGINAL_TOPIC_HEADER, source)),
                readInt(headers, ORIGINAL_PARTITION_HEADER, source),
                readLong(headers, ORIGINAL_OFFSET_HEADER, source)
        );
    }

    private static boolean hasOriginalPosition(Headers headers) {
        return headers.lastHeader(ORIGINAL_TOPIC_HEADER) != null
                && headers.lastHeader(ORIGINAL_PARTITION_HEADER) != null
                && headers.lastHeader(ORIGINAL_OFFSET_HEADER) != null;
    }

    private static String readString(Headers headers, String name, String source) {
        Header header = requiredHeader(headers, name, source);
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static int readInt(Headers headers, String name, String source) {
        Header header = requiredHeader(headers, name, source);
        if (header.value().length != Integer.BYTES) {
            throw invalidHeader(name, source);
        }
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private static long readLong(Headers headers, String name, String source) {
        Header header = requiredHeader(headers, name, source);
        if (header.value().length != Long.BYTES) {
            throw invalidHeader(name, source);
        }
        return ByteBuffer.wrap(header.value()).getLong();
    }

    private static Header requiredHeader(Headers headers, String name, String source) {
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            throw invalidHeader(name, source);
        }
        return header;
    }

    private static InvalidHotArticleEventException invalidHeader(String name, String source) {
        return new InvalidHotArticleEventException("%s has an invalid %s header".formatted(source, name));
    }
}
