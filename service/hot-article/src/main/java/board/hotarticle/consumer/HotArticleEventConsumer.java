package board.hotarticle.consumer;

import board.common.event.Event;
import board.common.event.EventConsumeMetrics;
import board.common.event.EventPayload;
import board.common.event.payload.EventType;
import board.hotarticle.kafka.HotArticleKafkaTopics;
import board.hotarticle.service.HotArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
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
    public void listen(
            String message,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long receivedTimestamp,
            Acknowledgment ack
    ) {
        log.info("[HotArticleEventConsumer.listen] received message={}", message);
        long startNanos = System.nanoTime();
        Event<EventPayload> event = Event.fromJson(message);
        if (event == null) {
            eventConsumeMetrics.recordIgnored(null);
            ack.acknowledge();
            return;
        }
        try {
            hotArticleService.handleEvent(event);
            eventConsumeMetrics.recordSuccess(event.getType(), startNanos, receivedTimestamp);
        } catch (Exception e) {
            eventConsumeMetrics.recordFailure(event.getType(), startNanos);
            // 실패를 집계만 하고 예외는 그대로 올린다. ack를 건너뛰어 재전달되게 하는 기존 동작을 유지해야
            // 처리 실패한 이벤트가 조용히 사라지지 않는다.
            throw e;
        }
        ack.acknowledge();
    }
}
