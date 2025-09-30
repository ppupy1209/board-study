package board.hotarticle.service.response;

import board.hotarticle.client.ArticleClient;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class HotArticleResponse {
    private Long articleId;
    private String title;
    private LocalDateTime createAt;

    public static HotArticleResponse from(ArticleClient.ArticleResponse articleResponse) {
        HotArticleResponse hotArticleResponse = new HotArticleResponse();
        hotArticleResponse.articleId = articleResponse.getArticleId();
        hotArticleResponse.title = articleResponse.getTitle();
        hotArticleResponse.createAt = articleResponse.getCreatedAt();
        return hotArticleResponse;
    }
}
