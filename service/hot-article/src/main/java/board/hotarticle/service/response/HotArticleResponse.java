package board.hotarticle.service.response;

import board.hotarticle.client.ArticleClient;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
public class HotArticleResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;
    private String title;
    private LocalDateTime createdAt;

    public static HotArticleResponse from(ArticleClient.ArticleResponse articleResponse) {
        HotArticleResponse hotArticleResponse = new HotArticleResponse();
        hotArticleResponse.articleId = articleResponse.getArticleId();
        hotArticleResponse.title = articleResponse.getTitle();
        hotArticleResponse.createdAt = articleResponse.getCreatedAt();
        return hotArticleResponse;
    }
}
