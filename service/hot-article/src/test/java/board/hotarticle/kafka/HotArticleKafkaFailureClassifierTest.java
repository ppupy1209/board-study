package board.hotarticle.kafka;

import board.common.event.payload.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class HotArticleKafkaFailureClassifierTest {
    private final HotArticleKafkaFailureClassifier classifier = new HotArticleKafkaFailureClassifier();

    @Test
    void classifiesRedisConnectionFailureInsideWrapperAsRetryable() {
        RuntimeException wrapped = new RuntimeException(
                "listener failed",
                new RedisConnectionFailureException("redis unavailable")
        );

        assertThat(classifier.isRetryable(wrapped)).isTrue();
    }

    @Test
    void classifiesProcessingLockContentionAsRetryable() {
        HotArticleEventPosition position = new HotArticleEventPosition(
                EventType.Topic.BOARD_ARTICLE,
                0,
                1L
        );

        assertThat(classifier.isRetryable(
                new HotArticleEventProcessingInProgressException(position, 1L)
        )).isTrue();
    }

    @Test
    void classifiesInvalidEventAsPermanentFailure() {
        assertThat(classifier.isRetryable(
                new InvalidHotArticleEventException("invalid payload")
        )).isFalse();
    }
}
