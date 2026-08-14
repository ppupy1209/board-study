package board.hotarticle.consumer;

import board.common.event.Event;
import board.common.event.EventConsumeMetrics;
import board.common.event.EventPayload;
import board.common.event.payload.ArticleViewedEventPayload;
import board.common.event.payload.EventType;
import board.hotarticle.kafka.HotArticleEventPosition;
import board.hotarticle.kafka.InvalidHotArticleEventException;
import board.hotarticle.service.HotArticleService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotArticleEventConsumerTest {
    @Mock
    private HotArticleService hotArticleService;
    @Mock
    private EventConsumeMetrics eventConsumeMetrics;
    @Mock
    private Acknowledgment acknowledgment;

    private HotArticleEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new HotArticleEventConsumer(hotArticleService, eventConsumeMetrics);
    }

    @Test
    void acknowledgesSuccessfullyHandledEvent() {
        ConsumerRecord<String, String> record = validRecord(3L);
        HotArticleEventPosition position = new HotArticleEventPosition(
                EventType.Topic.BOARD_VIEW,
                0,
                3L
        );
        when(hotArticleService.handleIfLatest(any(), eq(position))).thenReturn(true);

        consumer.listen(record, acknowledgment);

        verify(eventConsumeMetrics).recordSuccess(
                eq(EventType.ARTICLE_VIEWED),
                anyLong(),
                eq(record.timestamp())
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void acknowledgesStaleEventWithoutProcessingItAgain() {
        ConsumerRecord<String, String> record = validRecord(3L);
        when(hotArticleService.handleIfLatest(any(), any(HotArticleEventPosition.class))).thenReturn(false);

        consumer.listen(record, acknowledgment);

        verify(eventConsumeMetrics).recordIgnored(EventType.ARTICLE_VIEWED);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void rejectsMalformedEventWithoutAcknowledgingIt() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                EventType.Topic.BOARD_VIEW,
                0,
                3L,
                "1",
                "not-json"
        );

        assertThatThrownBy(() -> consumer.listen(record, acknowledgment))
                .isInstanceOf(InvalidHotArticleEventException.class);

        verify(eventConsumeMetrics).recordFailure(isNull(), anyLong());
        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<String, String> validRecord(long offset) {
        ArticleViewedEventPayload payload = ArticleViewedEventPayload.builder()
                .articleId(1L)
                .articleViewCount(10L)
                .build();
        String message = Event.of(100L, EventType.ARTICLE_VIEWED, payload).toJson();
        return new ConsumerRecord<>(
                EventType.Topic.BOARD_VIEW,
                0,
                offset,
                "1",
                message
        );
    }
}
