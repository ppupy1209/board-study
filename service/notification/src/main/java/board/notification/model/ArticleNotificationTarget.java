package board.notification.model;

import board.notification.client.ArticleClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleNotificationTarget {
    private Long articleId;
    private Long writerId;
    private String title;

    public static ArticleNotificationTarget from(ArticleClient.ArticleResponse response) {
        return new ArticleNotificationTarget(response.getArticleId(), response.getWriterId(), response.getTitle());
    }
}
