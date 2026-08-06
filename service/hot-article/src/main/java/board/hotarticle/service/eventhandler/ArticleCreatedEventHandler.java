package board.hotarticle.service.eventhandler;

import board.common.event.Event;
import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.EventType;
import board.hotarticle.repository.ArticleCreatedTimeRepository;
import board.hotarticle.repository.HotArticleQueryModel;
import board.hotarticle.repository.HotArticleQueryModelRepository;
import board.hotarticle.utils.TimeCalculatorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ArticleCreatedEventHandler implements EventHandler<ArticleCreatedEventPayload> {
    private final ArticleCreatedTimeRepository articleCreatedTimeRepository;
    private final HotArticleQueryModelRepository hotArticleQueryModelRepository;

    @Override
    public void handle(Event<ArticleCreatedEventPayload> event) {
        ArticleCreatedEventPayload payload = event.getPayload();
        if (!TimeCalculatorUtils.isActiveHotArticleDate(payload.getCreatedAt())) {
            return;
        }

        Duration ttl = TimeCalculatorUtils.calculateDurationToExpiration(payload.getCreatedAt());
        articleCreatedTimeRepository.createOrUpdate(
                payload.getArticleId(),
                payload.getCreatedAt(),
                ttl
        );
        hotArticleQueryModelRepository.createOrUpdate(HotArticleQueryModel.create(payload), ttl);
    }

    @Override
    public boolean supports(Event<ArticleCreatedEventPayload> event) {
        return EventType.ARTICLE_CREATED == event.getType();
    }

    @Override
    public Long findArticleId(Event<ArticleCreatedEventPayload> event) {
        return event.getPayload().getArticleId();
    }
}
