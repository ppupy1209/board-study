package board.search.service;

import board.common.event.Event;
import board.common.event.EventConsumeMetrics;
import board.common.event.EventPayload;
import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.ArticleDeletedEventPayload;
import board.common.event.payload.ArticleUpdatedEventPayload;
import board.common.event.payload.EventType;
import board.search.document.ArticleDocument;
import board.search.repository.ElasticsearchArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArticleSearchEventConsumer {
    private final ElasticsearchArticleRepository repository;
    private final EventConsumeMetrics eventConsumeMetrics;

    @KafkaListener(topics = EventType.Topic.BOARD_ARTICLE)
    public void listen(
            String message,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long receivedTimestamp,
            Acknowledgment acknowledgment
    ) {
        long startNanos = System.nanoTime();
        Event<EventPayload> event = Event.fromJson(message);
        if (event == null || event.getType() == null) {
            eventConsumeMetrics.recordIgnored(null);
            acknowledgment.acknowledge();
            return;
        }
        try {
            switch (event.getType()) {
                case ARTICLE_CREATED -> repository.index(ArticleDocument.from(
                        (ArticleCreatedEventPayload) event.getPayload()
                ));
                case ARTICLE_UPDATED -> repository.index(ArticleDocument.from(
                        (ArticleUpdatedEventPayload) event.getPayload()
                ));
                case ARTICLE_DELETED -> repository.delete(
                        ((ArticleDeletedEventPayload) event.getPayload()).getArticleId()
                );
                default -> {
                    eventConsumeMetrics.recordIgnored(event.getType());
                    acknowledgment.acknowledge();
                    return;
                }
            }
            eventConsumeMetrics.recordSuccess(event.getType(), startNanos, receivedTimestamp);
        } catch (RuntimeException exception) {
            eventConsumeMetrics.recordFailure(event.getType(), startNanos);
            throw exception;
        }
        acknowledgment.acknowledge();
    }
}
