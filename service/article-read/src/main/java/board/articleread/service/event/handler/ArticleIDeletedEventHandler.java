package board.articleread.service.event.handler;

import board.articleread.repository.ArticleIdListRepository;
import board.articleread.repository.ArticleQueryModelRepository;
import board.articleread.repository.BoardArticleCountRepository;
import board.common.event.Event;
import board.common.event.payload.ArticleDeletedEventPayload;
import board.common.event.payload.ArticleUpdatedEventPayload;
import board.common.event.payload.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArticleIDeletedEventHandler implements EventHandler<ArticleDeletedEventPayload>{
    private final ArticleQueryModelRepository articleQueryModelRepository;
    private final ArticleIdListRepository articleIdListRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;
    @Override
    public void handle(Event<ArticleDeletedEventPayload> event) {
        ArticleDeletedEventPayload payload = event.getPayload();
        articleIdListRepository.delete(payload.getBoardId(), payload.getArticleId());
        articleQueryModelRepository.delete(payload.getArticleId());
        boardArticleCountRepository.createOrUpdate(payload.getBoardId(), payload.getBoardArticleCount());
    }

    @Override
    public boolean supports(Event<ArticleDeletedEventPayload> event) {
        return EventType.ARTICLE_DELETED == event.getType();
    }
}
