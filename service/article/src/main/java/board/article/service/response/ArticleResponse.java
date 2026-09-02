package board.article.service.response;

import board.article.entity.Article;
import board.article.entity.ArticleWriter;
import board.article.entity.WriterType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
public class ArticleResponse {
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

    public static ArticleResponse from(Article article) {
        return from(article, null, null);
    }

    public static ArticleResponse from(Article article, ArticleWriter writer) {
        return from(
                article,
                writer == null ? null : writer.getWriterType(),
                writer == null ? null : writer.getWriterNickname()
        );
    }

    public static ArticleResponse from(Article article, WriterType writerType, String writerNickname) {
        ArticleResponse response = new ArticleResponse();
        response.articleId = article.getArticleId();
        response.title = article.getTitle();
        response.content = article.getContent();
        response.boardId = article.getBoardId();
        response.writerId = article.getWriterId();
        response.writerType = writerType == null ? null : writerType.name();
        response.writerNickname = writerNickname;
        response.createdAt = article.getCreatedAt();
        response.modifiedAt = article.getModifiedAt();
        return response;
    }
}
