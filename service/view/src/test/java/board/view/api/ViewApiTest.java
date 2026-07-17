package board.view.api;

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
public class ViewApiTest {
    RestClient restClient = RestClient.create("http://localhost:9003");

    @Test
    void viewTest() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(10000);
        for (int i = 0; i < 10000; i++) {
            executorService.submit(() -> {
                restClient.post()
                        .uri("/v1/article-views/articles/{articleId}/users/{userId}", 4L, 1L)
                        .retrieve()
                        .toBodilessEntity();
                latch.countDown();
            });
        }

        latch.await();

        Long count = restClient.get()
                .uri("/v1/article-views/articles/{articleId}/count", 4L)
                .retrieve()
                .body(Long.class);

        System.out.println("count = " + count);
    }
}
