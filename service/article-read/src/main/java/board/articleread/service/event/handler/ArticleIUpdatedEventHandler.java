package board.articleread.service.event.handler;

import board.articleread.repository.ArticleQueryModel;
import board.articleread.repository.ArticleQueryModelRepository;
import board.common.event.Event;
import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.ArticleUpdatedEventPayload;
import board.common.event.payload.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ArticleIUpdatedEventHandler implements EventHandler<ArticleUpdatedEventPayload>{
    private final ArticleQueryModelRepository articleQueryModelRepository;

    @Override
    public void handle(Event<ArticleUpdatedEventPayload> event) {
        articleQueryModelRepository.read(event.getPayload().getArticleId())
                .ifPresent(articleQueryModel -> {
                    articleQueryModel.updateBy(event.getPayload());
                    articleQueryModelRepository.update(articleQueryModel);
                });
    }

    @Override
    public boolean supports(Event<ArticleUpdatedEventPayload> event) {
        return EventType.ARTICLE_UPDATED == event.getType();
    }
}
