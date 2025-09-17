package board.article.api;

import board.article.service.response.ArticlePageResponse;
import board.article.service.response.ArticleResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

public class ArticleApiTest {
    RestClient restClient = RestClient.create("http://localhost:9000");

    @Test
    void create() {
        ArticleResponse response = create(new ArticleCreateRequest(
                "hi", "content", 1L, 1L
        ));
        System.out.println("response = " + response);
    }

    ArticleResponse create(ArticleCreateRequest request) {
        return restClient.post()
                .uri("/v1/articles")
                .body(request)
                .retrieve()
                .body(ArticleResponse.class);
    }

    @Test
    void read() {
        ArticleResponse response = read(212865095779332096L);
        System.out.println("response = " + response);
    }

    @Test
    void update() {
        update(212865095779332096L);
        ArticleResponse response = read(212865095779332096L);
        System.out.println("response = " + response);
    }

    void update(Long articleId) {
        restClient.put()
                .uri("/v1/articles/{articleId}", articleId)
                .body(new ArticleUpdateRequest("hi2","content2"))
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void delete() {
        restClient.delete()
                .uri("/v1/articles/{articleId}", 212865095779332096L)
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void readAll() {
        ArticlePageResponse response = restClient.get()
                .uri("/v1/articles?boardId=1&page=1&pageSize=30")
                .retrieve()
                .body(ArticlePageResponse.class);

        System.out.println("response.getArticleCount() = " + response.getArticleCount());
        for (ArticleResponse article : response.getArticles()) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }
    }
    
    @Test
    void readAllInfiniteScrollTest() {
        List<ArticleResponse> articles = restClient.get()
                .uri("/v1/articles/infinite-scroll?boardId=1&pageSize=5")
                .retrieve()
                .body(new ParameterizedTypeReference<List<ArticleResponse>>() {
                });

        for (ArticleResponse article : articles) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }

        Long lastArticleId = articles.getLast().getArticleId();
        System.out.println("lastArticleId = " + lastArticleId);

        List<ArticleResponse> articles2 = restClient.get()
                .uri("/v1/articles/infinite-scroll?boardId=1&pageSize=5&lastArticleId=%s".formatted(lastArticleId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ArticleResponse>>() {
                });
        for (ArticleResponse article : articles2) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }
    }

    ArticleResponse read(Long articleId) {
        return restClient.get()
                .uri("/v1/articles/{articleId}",articleId)
                .retrieve()
                .body(ArticleResponse.class);
    }

    @Test
    void countTest() {
        ArticleResponse response = create(new ArticleCreateRequest("hi", "content", 1L, 2L));

        Long count = restClient.get()
                .uri("/v1/articles/boards/{boardId}/count", 2L)
                .retrieve()
                .body(Long.class);
        System.out.println("count = " + count);

        restClient.delete()
                .uri("/v1/articles/{articleId}", response.getArticleId())
                .retrieve()
                .toBodilessEntity();

        Long count2 = restClient.get()
                .uri("/v1/articles/boards/{boardId}/count", 2L)
                .retrieve()
                .body(Long.class);
        System.out.println("count = " + count2);
    }

    @Getter
    @AllArgsConstructor
    static class ArticleCreateRequest {
        private String title;
        private String content;
        private Long writerId;
        private Long boardId;
    }

    @Getter
    @AllArgsConstructor
    static class ArticleUpdateRequest {
        private String title;
        private String content;
    }
}
