package board.articleread.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.random.RandomGenerator;

/**
 * [수동 실행 전용 도구 — 자동 테스트가 아니다]
 *
 * 실행 중인 서비스에 API로 게시글/댓글/좋아요를 만든다.
 *
 * <p>@Test가 붙어 있어 `./gradlew test` 실행 시 함께 돌아가며, 그 결과 로컬 대용량 데이터셋을 오염시킨다.
 * 실제로 이 클래스 때문에 자유게시판이 15,000,100건에서 27,000,102건으로 늘어난 적이 있다.
 * 그래서 기본 실행에서는 제외하고, 필요할 때만 아래처럼 명시적으로 실행한다.
 *
 * 실행이 필요하면 아래 @Disabled를 잠시 제거하고 다음을 실행한 뒤, 반드시 다시 되돌린다.
 *
 * <pre>
 * ./gradlew :service:article-read:test --tests "*DataInitializer"
 * </pre>
 */
@Disabled("수동 실행 전용 데이터 생성 도구. 자동 실행되면 로컬 대용량 데이터셋을 오염시킨다. 필요할 때만 @Disabled를 잠시 제거하고 실행한다.")
public class DataInitializer {
    RestClient articleServiceClient = RestClient.create("http://localhost:9000");
    RestClient commentServiceClient = RestClient.create("http://localhost:9001");
    RestClient likeServiceClient = RestClient.create("http://localhost:9002");
    RestClient viewServiceClient = RestClient.create("http://localhost:9003");

    @Test
    void initializer() {
        for (int i=0; i<30; i++) {
            Long articleId = createArticle();
            System.out.println("articleId = " + articleId);
            long commentCount = RandomGenerator.getDefault().nextLong(10);
            long likeCount = RandomGenerator.getDefault().nextLong(10);
            long viewCount = RandomGenerator.getDefault().nextLong(200);

            createComment(articleId, commentCount);
            like(articleId, likeCount);
            view(articleId, viewCount);
        }
    }

    private void view(Long articleId, long viewCount) {
        while (viewCount-- > 0) {
            viewServiceClient.post()
                    .uri("/v1/article-views/articles/{articleId}/users/{userId}", articleId, viewCount)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private void like(Long articleId, long likeCount) {
        while (likeCount-- > 0) {
            likeServiceClient.post()
                    .uri("/v1/article-likes/articles/{articleId}/users/{userId}/pessimistic-lock-1",articleId,likeCount)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private void createComment(Long articleId, long commentCount) {
        while (commentCount-- > 0) {
            commentServiceClient.post()
                    .uri("/v2/comments")
                    .body(new CommentCreateRequest(articleId, "content",1L))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    @Getter
    @AllArgsConstructor
    static class CommentCreateRequest {
        private Long articleId;
        private String content;
        private Long writerId;
    }

    Long createArticle() {
        return articleServiceClient.post()
                .uri("/v1/articles")
                .body(new ArticleCreateRequest("title", "content", 1L, 1L))
                .retrieve()
                .body(ArticleResponse.class)
                .getArticleId();
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
    static class ArticleResponse {
        private Long articleId;
    }
}
