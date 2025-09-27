package board.common.event;

import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.EventType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void serde() {
        ArticleCreatedEventPayload payload = ArticleCreatedEventPayload.builder()
                .articleId(1L)
                .title("title")
                .content("content")
                .boardId(1L)
                .writerId(1L)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .boardArticleCount(23L)
                .build();

        Event<EventPayload> event = Event.of(
                1234L,
                EventType.ARTICLE_CREATED,
                payload
        );

        String json = event.toJson();
        System.out.println("json = " + json);

        Event<EventPayload> result = Event.fromJson(json);
        assertThat(result.getEventId()).isEqualTo(event.getEventId());
        assertThat(result.getPayload()).isInstanceOf(ArticleCreatedEventPayload.class);

        ArticleCreatedEventPayload resultPayload = (ArticleCreatedEventPayload) result.getPayload();
        System.out.println("resultPayload = " + resultPayload);
    }
}