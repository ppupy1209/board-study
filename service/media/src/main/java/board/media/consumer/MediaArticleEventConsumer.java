package board.media.consumer;

import board.common.event.Event;
import board.common.event.EventPayload;
import board.common.event.payload.ArticleDeletedEventPayload;
import board.common.event.payload.EventType;
import board.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaArticleEventConsumer {
    private final MediaService mediaService;

    @KafkaListener(
            topics = EventType.Topic.BOARD_ARTICLE,
            groupId = "modu-square-media-article"
    )
    public void process(String message) {
        Event<EventPayload> event = Event.fromJson(message);
        if (event == null || event.getType() != EventType.ARTICLE_DELETED) {
            return;
        }

        ArticleDeletedEventPayload payload = (ArticleDeletedEventPayload) event.getPayload();
        mediaService.deleteByArticleId(payload.getArticleId());
        log.info("게시글 삭제에 연결된 이미지를 정리했습니다. articleId={}", payload.getArticleId());
    }
}