package board.hotarticle.service.eventhandler;

import board.common.event.Event;
import board.common.event.payload.CommentCreatedEventPayload;
import board.common.event.payload.EventType;
import board.hotarticle.repository.ArticleCommentCountRepository;
import board.hotarticle.utils.TimeCalculatorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CommentCreatedEventHandler implements EventHandler<CommentCreatedEventPayload>{
    private final ArticleCommentCountRepository articleCommentCountRepository;

    @Override
    public void handle(Event<CommentCreatedEventPayload> event) {
        handle(event, TimeCalculatorUtils.calculateDurationToMidnight());
    }

    @Override
    public void handle(Event<CommentCreatedEventPayload> event, Duration ttl) {
        CommentCreatedEventPayload payload = event.getPayload();
        articleCommentCountRepository.createOrUpdate(
                payload.getArticleId(),
                payload.getArticleCommentCount(),
                ttl
        );
    }

    @Override
    public boolean supports(Event<CommentCreatedEventPayload> event) {
        return EventType.COMMENT_CREATED == event.getType();
    }

    @Override
    public Long findArticleId(Event<CommentCreatedEventPayload> event) {
        return event.getPayload().getArticleId();
    }
}
