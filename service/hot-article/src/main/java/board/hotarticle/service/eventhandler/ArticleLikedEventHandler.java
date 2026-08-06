package board.hotarticle.service.eventhandler;

import board.common.event.Event;
import board.common.event.payload.ArticleLikedEventPayload;
import board.common.event.payload.EventType;
import board.hotarticle.repository.ArticleLikeCountRepository;
import board.hotarticle.utils.TimeCalculatorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ArticleLikedEventHandler implements EventHandler<ArticleLikedEventPayload>{
    private final ArticleLikeCountRepository articleLikeCountRepository;

    @Override
    public void handle(Event<ArticleLikedEventPayload> event) {
        handle(event, TimeCalculatorUtils.calculateDurationToMidnight());
    }

    @Override
    public void handle(Event<ArticleLikedEventPayload> event, Duration ttl) {
        ArticleLikedEventPayload payload = event.getPayload();
        articleLikeCountRepository.createOrUpdate(
                payload.getArticleId(),
                payload.getArticleLikeCount(),
                ttl
        );
    }

    @Override
    public boolean supports(Event<ArticleLikedEventPayload> event) {
        return EventType.ARTICLE_LIKED == event.getType();
    }

    @Override
    public Long findArticleId(Event<ArticleLikedEventPayload> event) {
        return event.getPayload().getArticleId();
    }
}
