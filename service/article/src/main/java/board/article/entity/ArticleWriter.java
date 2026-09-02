package board.article.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "article_writer")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleWriter {
    @Id
    private Long articleId;
    @Enumerated(EnumType.STRING)
    private WriterType writerType;
    private String writerNickname;

    public static ArticleWriter member(Long articleId, String writerNickname) {
        ArticleWriter writer = new ArticleWriter();
        writer.articleId = articleId;
        writer.writerType = WriterType.MEMBER;
        writer.writerNickname = writerNickname;
        return writer;
    }
}
