package board.search.document;

import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.ArticleUpdatedEventPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDocument {
    private Long articleId;
    private String title;
    private String content;
    private Long boardId;
    private Long writerId;
    private String createdAt;
    private String modifiedAt;

    public static ArticleDocument from(ArticleCreatedEventPayload payload) {
        return ArticleDocument.builder()
                .articleId(payload.getArticleId())
                .title(payload.getTitle())
                .content(payload.getContent())
                .boardId(payload.getBoardId())
                .writerId(payload.getWriterId())
                .createdAt(payload.getCreatedAt().toString())
                .modifiedAt(payload.getModifiedAt().toString())
                .build();
    }

    public static ArticleDocument from(ArticleUpdatedEventPayload payload) {
        return ArticleDocument.builder()
                .articleId(payload.getArticleId())
                .title(payload.getTitle())
                .content(payload.getContent())
                .boardId(payload.getBoardId())
                .writerId(payload.getWriterId())
                .createdAt(payload.getCreatedAt().toString())
                .modifiedAt(payload.getModifiedAt().toString())
                .build();
    }
}
