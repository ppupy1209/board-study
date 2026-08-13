package board.hotarticle.kafka;

import board.common.event.payload.EventType;

import java.util.Set;

public final class HotArticleKafkaTopics {
    public static final String DLQ_SUFFIX = ".hot-article.DLQ";
    public static final String REPLAY_SUFFIX = ".hot-article.REPLAY";
    public static final String PARKING_SUFFIX = ".hot-article.PARKING";
    public static final String DLQ_REPLAY_GROUP = "modu-square-hot-article-dlq-replay";

    public static final String BOARD_ARTICLE_DLQ = EventType.Topic.BOARD_ARTICLE + DLQ_SUFFIX;
    public static final String BOARD_COMMENT_DLQ = EventType.Topic.BOARD_COMMENT + DLQ_SUFFIX;
    public static final String BOARD_LIKE_DLQ = EventType.Topic.BOARD_LIKE + DLQ_SUFFIX;
    public static final String BOARD_VIEW_DLQ = EventType.Topic.BOARD_VIEW + DLQ_SUFFIX;
    public static final String BOARD_ARTICLE_REPLAY = EventType.Topic.BOARD_ARTICLE + REPLAY_SUFFIX;
    public static final String BOARD_COMMENT_REPLAY = EventType.Topic.BOARD_COMMENT + REPLAY_SUFFIX;
    public static final String BOARD_LIKE_REPLAY = EventType.Topic.BOARD_LIKE + REPLAY_SUFFIX;
    public static final String BOARD_VIEW_REPLAY = EventType.Topic.BOARD_VIEW + REPLAY_SUFFIX;

    private static final Set<String> ORIGINAL_TOPICS = Set.of(
            EventType.Topic.BOARD_ARTICLE,
            EventType.Topic.BOARD_COMMENT,
            EventType.Topic.BOARD_LIKE,
            EventType.Topic.BOARD_VIEW
    );

    private HotArticleKafkaTopics() {
    }

    public static String dlqTopic(String sourceTopic) {
        return originalTopic(sourceTopic) + DLQ_SUFFIX;
    }

    public static String replayTopic(String originalTopic) {
        requireOriginalTopic(originalTopic);
        return originalTopic + REPLAY_SUFFIX;
    }

    public static String parkingTopic(String originalTopic) {
        requireOriginalTopic(originalTopic);
        return originalTopic + PARKING_SUFFIX;
    }

    public static void requireOriginalTopic(String topic) {
        if (!ORIGINAL_TOPICS.contains(topic)) {
            throw new IllegalArgumentException("Unsupported hot-article topic: " + topic);
        }
    }

    public static String originalTopic(String sourceTopic) {
        if (ORIGINAL_TOPICS.contains(sourceTopic)) {
            return sourceTopic;
        }
        if (sourceTopic.endsWith(REPLAY_SUFFIX)) {
            String originalTopic = sourceTopic.substring(0, sourceTopic.length() - REPLAY_SUFFIX.length());
            requireOriginalTopic(originalTopic);
            return originalTopic;
        }
        throw new IllegalArgumentException("Unsupported hot-article source topic: " + sourceTopic);
    }
}
