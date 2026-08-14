package board.hotarticle.kafka;

public class HotArticleEventProcessingInProgressException extends RuntimeException {
    public HotArticleEventProcessingInProgressException(HotArticleEventPosition position, Long articleId) {
        super("Another hot-article event is being processed: position=%s, articleId=%s"
                .formatted(position, articleId));
    }
}
