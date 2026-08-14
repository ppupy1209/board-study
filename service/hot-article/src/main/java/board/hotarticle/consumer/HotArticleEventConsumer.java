package board.hotarticle.consumer;

import board.common.event.Event;
import board.common.event.EventConsumeMetrics;
import board.common.event.EventPayload;
import board.common.event.payload.EventType;
import board.hotarticle.kafka.HotArticleEventPosition;
import board.hotarticle.kafka.HotArticleKafkaTopics;
import board.hotarticle.kafka.InvalidHotArticleEventException;
import board.hotarticle.service.HotArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleEventConsumer {
    private final HotArticleService hotArticleService;
    private final EventConsumeMetrics eventConsumeMetrics;

    @KafkaListener(topics = {
            EventType.Topic.BOARD_ARTICLE,
            EventType.Topic.BOARD_COMMENT,
            EventType.Topic.BOARD_LIKE,
            EventType.Topic.BOARD_VIEW,
            HotArticleKafkaTopics.BOARD_ARTICLE_REPLAY,
            HotArticleKafkaTopics.BOARD_COMMENT_REPLAY,
            HotArticleKafkaTopics.BOARD_LIKE_REPLAY,
            HotArticleKafkaTopics.BOARD_VIEW_REPLAY,
    })
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.info(
                "[HotArticleEventConsumer.listen] topic={}, partition={}, offset={}",
                record.topic(),
                record.partition(),
                record.offset()
        );
        long startNanos = System.nanoTime();
        Event<EventPayload> event = null;
        try {
            event = parseEvent(record.value());
            HotArticleEventPosition position = HotArticleEventPosition.from(record);
            boolean handled = hotArticleService.handleIfLatest(event, position);
            if (handled) {
                eventConsumeMetrics.recordSuccess(event.getType(), startNanos, record.timestamp());
            } else {
                eventConsumeMetrics.recordIgnored(event.getType());
            }
        } catch (Exception e) {
            eventConsumeMetrics.recordFailure(event == null ? null : event.getType(), startNanos);
            throw e;
        }
        acknowledgment.acknowledge();
    }

    private Event<EventPayload> parseEvent(String message) {
        try {
            Event<EventPayload> event = Event.fromJson(message);
            if (event == null
                    || event.getEventId() == null
                    || event.getType() == null
                    || event.getPayload() == null) {
                throw new InvalidHotArticleEventException("Hot-article event is missing a required field");
            }
            return event;
        } catch (InvalidHotArticleEventException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidHotArticleEventException("Hot-article event cannot be deserialized", e);
        }
    }
}
