package board.like.service.response;

import board.like.entity.ArticleLike;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter @ToString
public class ArticleLikeResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleLikeId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private LocalDateTime createdAt;

    public static ArticleLikeResponse from(ArticleLike articleLike) {
        ArticleLikeResponse response = new ArticleLikeResponse();
        response.articleLikeId = articleLike.getArticleLikeId();
        response.articleId = articleLike.getArticleId();
        response.userId = articleLike.getUserId();
        response.createdAt = articleLike.getCreatedAt();
        return response;
    }
}
