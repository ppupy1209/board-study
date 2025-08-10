package board.article.api;

import board.article.service.response.ArticleResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

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

    ArticleResponse read(Long articleId) {
        return restClient.get()
                .uri("/v1/articles/{articleId}",articleId)
                .retrieve()
                .body(ArticleResponse.class);
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
