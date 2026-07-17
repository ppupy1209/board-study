package board.articleread.api;

import board.articleread.service.response.ArticleReadPageResponse;
import board.articleread.service.response.ArticleReadResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * [수동 실행 전용 도구 — 자동 테스트가 아니다]
 *
 * 실행 중인 서비스에 실제 HTTP 요청을 보내는 탐색용 코드다. 단정문(assert) 없이 응답을 출력만 한다.
 * 따라서 다음 이유로 `./gradlew test`에서 제외한다.
 *
 * <ul>
 *   <li>서비스가 떠 있어야만 동작한다 (CI/클린 체크아웃에서 반드시 실패한다)</li>
 *   <li>하드코딩된 ID를 사용해 다른 DB 상태에서는 실패한다</li>
 *   <li>실제 데이터를 생성해 로컬 대용량 데이터셋을 오염시킨다</li>
 * </ul>
 *
 * 실행이 필요하면 서비스를 띄운 뒤 아래 @Disabled를 잠시 제거하고 실행한다.
 */
@Disabled("실행 중인 서비스가 필요한 수동 탐색용 도구. 자동 실행 시 실패하거나 데이터를 오염시킨다.")
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
