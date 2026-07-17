package board.articleread.repository;

import board.articleread.client.ArticleClient;
import board.common.event.payload.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ArticleQueryModel {
    private Long articleId;
    private String title;
    private String content;
    private Long boardId;
    private Long writerId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private Long articleCommentCount;
    private Long articleLikeCount;

    public static ArticleQueryModel create(ArticleCreatedEventPayload payload) {
        ArticleQueryModel model = new ArticleQueryModel();
        model.articleId = payload.getArticleId();
        model.title = payload.getTitle();
        model.content = payload.getContent();
        model.boardId = payload.getBoardId();
        model.writerId = payload.getWriterId();
        model.createdAt = payload.getCreatedAt();
        model.modifiedAt = payload.getModifiedAt();
        model.articleCommentCount = 0L;
        model.articleLikeCount = 0L;
        return model;
    }

    public static ArticleQueryModel create(ArticleClient.ArticleResponse article, Long commentCount, Long likeCount) {
        ArticleQueryModel model = new ArticleQueryModel();
        model.articleId = article.getArticleId();
        model.title = article.getTitle();
        model.content = article.getContent();
        model.boardId = article.getBoardId();
        model.writerId = article.getWriterId();
        model.createdAt = article.getCreatedAt();
        model.modifiedAt = article.getModifiedAt();
        model.articleCommentCount = commentCount;
        model.articleLikeCount = likeCount;
        return model;
    }

    public void updateBy(CommentCreatedEventPayload payload) {
        this.articleCommentCount = payload.getArticleCommentCount();
    }

    public void updateBy(CommentDeletedEventPayload payload) {
        this.articleCommentCount = payload.getArticleCommentCount();
    }

    public void updateBy(ArticleLikedEventPayload payload) {
        this.articleLikeCount = payload.getArticleLikeCount();
    }

    public void updateBy(ArticleUnlikedEventPayload payload) {
        this.articleLikeCount = payload.getArticleLikeCount();
    }

    public void updateBy(ArticleUpdatedEventPayload payload) {
        this.title = payload.getTitle();
        this.content = payload.getContent();
        this.boardId = payload.getBoardId();
        this.writerId = payload.getWriterId();
        this.createdAt = payload.getCreatedAt();
        this.modifiedAt = payload.getModifiedAt();
    }
}
