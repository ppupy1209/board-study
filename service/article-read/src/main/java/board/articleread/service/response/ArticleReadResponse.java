package board.articleread.service.response;

import board.articleread.repository.ArticleQueryModel;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
public class ArticleReadResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;
    private String title;
    private String content;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long boardId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long writerId;
    private String writerType;
    private String writerNickname;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private Long articleCommentCount;
    private Long articleLikeCount;
    private Long articleViewCount;

    public static ArticleReadResponse from(ArticleQueryModel articleQueryModel, Long viewCount) {
        return from(
                articleQueryModel,
                articleQueryModel.getArticleCommentCount(),
                articleQueryModel.getArticleLikeCount(),
                viewCount
        );
    }

    public static ArticleReadResponse from(
            ArticleQueryModel articleQueryModel,
            Long commentCount,
            Long likeCount,
            Long viewCount
    ) {
        ArticleReadResponse response = new ArticleReadResponse();
        response.articleId = articleQueryModel.getArticleId();
        response.title = articleQueryModel.getTitle();
        response.content = articleQueryModel.getContent();
        response.boardId = articleQueryModel.getBoardId();
        response.writerId = articleQueryModel.getWriterId();
        response.writerType = articleQueryModel.getWriterType();
        response.writerNickname = articleQueryModel.getWriterNickname();
        response.createdAt = articleQueryModel.getCreatedAt();
        response.modifiedAt = articleQueryModel.getModifiedAt();
        response.articleCommentCount = commentCount;
        response.articleLikeCount = likeCount;
        response.articleViewCount = viewCount;
        return response;
    }
}
