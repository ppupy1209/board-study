package board.articleread.api;

import board.articleread.service.response.ArticleReadPageResponse;
import board.articleread.service.response.ArticleReadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

public class ArticleReadApiTest {
    RestClient articleReadRestClient = RestClient.create("http://localhost:9005");
    RestClient articleRestClient = RestClient.create("http://localhost:9000");

    @Test
    void readTest() {
        ArticleReadResponse response = articleReadRestClient.get()
                .uri("/v1/articles/{articleId}", 215808612405452800L)
                .retrieve()
                .body(ArticleReadResponse.class);

        System.out.println("response = " + response);
    }

    @Test
    void readAllTest() {
        ArticleReadPageResponse response1 = articleReadRestClient.get()
                .uri("/v1/articles?boardId=%s&page=%s&pageSize=%s".formatted(1L, 4000L, 5L))
                .retrieve()
                .body(ArticleReadPageResponse.class);

        System.out.println("response1.getArticleCount() = " + response1.getArticleCount());
        for (ArticleReadResponse article : response1.getArticles()) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }

        ArticleReadPageResponse response2 = articleRestClient.get()
                .uri("/v1/articles?boardId=%s&page=%s&pageSize=%s".formatted(1L, 4000L, 5L))
                .retrieve()
                .body(ArticleReadPageResponse.class);

        System.out.println("response2.getArticleCount() = " + response2.getArticleCount());
        for (ArticleReadResponse article : response2.getArticles()) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }
    }

    @Test
    void readAllInfiniteScrollTest() {
        List<ArticleReadResponse> responses1 = articleReadRestClient.get()
                .uri("/v1/articles/infinite-scroll?boardId=%s&pageSize=%s&lastArticleId=%s".formatted(1L, 5L, 235376676246712320L))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ArticleReadResponse>>() {
                });

        for (ArticleReadResponse article : responses1) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }

        System.out.println();

        List<ArticleReadResponse> responses2 = articleRestClient.get()
                .uri("/v1/articles/infinite-scroll?boardId=%s&pageSize=%s&lastArticleId=%s".formatted(1L, 5L, 235376676246712320L))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ArticleReadResponse>>() {
                });

        for (ArticleReadResponse article : responses2) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }
    }
}
