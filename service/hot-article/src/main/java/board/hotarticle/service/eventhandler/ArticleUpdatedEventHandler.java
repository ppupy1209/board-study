package board.hotarticle.service.eventhandler;

import board.common.event.Event;
import board.common.event.payload.ArticleUpdatedEventPayload;
import board.common.event.payload.EventType;
import board.hotarticle.repository.HotArticleQueryModel;
import board.hotarticle.repository.HotArticleQueryModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArticleUpdatedEventHandler implements EventHandler<ArticleUpdatedEventPayload> {
    private final HotArticleQueryModelRepository hotArticleQueryModelRepository;

    @Override
    public void handle(Event<ArticleUpdatedEventPayload> event) {
        hotArticleQueryModelRepository.createOrUpdate(HotArticleQueryModel.create(event.getPayload()));
    }

    @Override
    public boolean supports(Event<ArticleUpdatedEventPayload> event) {
        return EventType.ARTICLE_UPDATED == event.getType();
    }

    @Override
    public Long findArticleId(Event<ArticleUpdatedEventPayload> event) {
        return event.getPayload().getArticleId();
    }
}
