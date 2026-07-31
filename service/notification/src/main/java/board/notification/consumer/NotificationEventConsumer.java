package board.notification.consumer;

import board.common.event.Event;
import board.common.event.EventConsumeMetrics;
import board.common.event.EventPayload;
import board.common.event.payload.EventType;
import board.notification.service.NotificationService;
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
public class NotificationEventConsumer {
    private final NotificationService notificationService;
    private final EventConsumeMetrics eventConsumeMetrics;

    @KafkaListener(topics = {
            EventType.Topic.BOARD_ARTICLE,
            EventType.Topic.BOARD_COMMENT,
            EventType.Topic.BOARD_LIKE,
    })
    public void listen(
            String message,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long receivedTimestamp,
            Acknowledgment ack
    ) {
        long startNanos = System.nanoTime();
        Event<EventPayload> event = Event.fromJson(message);
        if (event == null || event.getType() == null) {
            eventConsumeMetrics.recordIgnored(null);
            ack.acknowledge();
            return;
        }

        try {
            notificationService.handleEvent(event);
            eventConsumeMetrics.recordSuccess(event.getType(), startNanos, receivedTimestamp);
            ack.acknowledge();
        } catch (Exception e) {
            eventConsumeMetrics.recordFailure(event.getType(), startNanos);
            log.error("[NotificationEventConsumer.listen] eventId={}", event.getEventId(), e);
            throw e;
        }
    }
}
