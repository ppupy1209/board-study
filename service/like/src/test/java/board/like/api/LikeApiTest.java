package board.like.api;

import board.like.service.response.ArticleLikeResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
public class LikeApiTest {
    RestClient restClient = RestClient.create("http://localhost:9002");

//    @Test
//    void likeAndUnlikeTest() {
//        Long articleId = 9999L;
//
//        like(articleId, 1L);
//        like(articleId, 2L);
//        like(articleId, 3L);
//
//        ArticleLikeResponse response1 = read(articleId, 1L);
//        ArticleLikeResponse response2 = read(articleId, 2L);
//        ArticleLikeResponse response3 = read(articleId, 3L);
//
//        System.out.println("response1 = " + response1);
//        System.out.println("response2 = " + response2);
//        System.out.println("response3 = " + response3);
//
//        unlike(articleId, 1L);
//        unlike(articleId, 2L);
//        unlike(articleId, 3L);
//    }

    @Test
    void likeTest() {
        like(1L, 1L, "pessimistic-lock-1");
    }

    void like(Long articleId, Long userId, String lockType) {
        restClient.post()
                .uri("/v1/article-likes/articles/{articleId}/users/{userId}/" + lockType, articleId, userId)
                .retrieve()
                .toBodilessEntity();
    }

    void unlike(Long articleId, Long userId) {
        restClient.delete()
                .uri("/v1/article-likes/articles/{articleId}/users/{userId}", articleId, userId)
                .retrieve()
                .toBodilessEntity();
    }

    ArticleLikeResponse read(Long articleId, Long userId) {
        return restClient.get()
                .uri("/v1/article-likes/articles/{articleId}/users/{userId}", articleId, userId)
                .retrieve()
                .body(ArticleLikeResponse.class);
    }

    @Test
    void likePerformanceTest() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(100);
//        likePerformanceTest(executorService, 1111L, "pessimistic-lock-1");
        likePerformanceTest(executorService, 2222L, "pessimistic-lock-2");
    }

    void likePerformanceTest(ExecutorService executorService, Long articleId, String lockType) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3000);
        System.out.println(lockType + " start");

        like(articleId, 1L, lockType);

        long start = System.nanoTime();
        for (int i = 0; i < 3000; i++) {
            long userId = i + 2L;
            executorService.submit(() -> {
                like(articleId, userId, lockType);
                latch.countDown();
            });
        }
        latch.await();

        long end = System.nanoTime();
        System.out.println("lockType = " + lockType + ", time = " + (end - start) / 1000000 + "ms");
        System.out.println(lockType + " end");

        Long count = restClient.get()
                .uri("/v1/article-likes/articles/{articleId}/count", articleId)
                .retrieve()
                .body(Long.class);

        System.out.println(count);
    }
}
