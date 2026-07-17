package board.comment.data;

import board.comment.entity.Comment;
import board.common.snowflake.Snowflake;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
/**
 * [수동 실행 전용 도구 — 자동 테스트가 아니다]
 *
 * 댓글 12,000,000건(2000 x 6000)을 bulk insert한다.
 *
 * <p>@Test가 붙어 있어 `./gradlew test` 실행 시 함께 돌아가며, 그 결과 로컬 대용량 데이터셋을 오염시킨다.
 * 실제로 이 클래스 때문에 자유게시판이 15,000,100건에서 27,000,102건으로 늘어난 적이 있다.
 * 그래서 기본 실행에서는 제외하고, 필요할 때만 아래처럼 명시적으로 실행한다.
 *
 * 실행이 필요하면 아래 @Disabled를 잠시 제거하고 다음을 실행한 뒤, 반드시 다시 되돌린다.
 *
 * <pre>
 * ./gradlew :service:comment:test --tests "*DataInitializer"
 * </pre>
 */
@Disabled("수동 실행 전용 데이터 생성 도구. 자동 실행되면 로컬 대용량 데이터셋을 오염시킨다. 필요할 때만 @Disabled를 잠시 제거하고 실행한다.")
public class DataInitializer {
    @PersistenceContext
    EntityManager em;
    @Autowired
    TransactionTemplate tx;
    Snowflake snowflake = new Snowflake();
    CountDownLatch latch = new CountDownLatch(EXECUTE_COUNT);

    static final int BULK_INSERT_SIZE = 2000;
    static final int EXECUTE_COUNT = 6000;

    @Test
    void initialize() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        for (int i = 0; i < EXECUTE_COUNT; i++) {
            executorService.submit(() -> {
                insert();
                latch.countDown();
                System.out.println("latch.getCount() = " + latch.getCount());
            });
        }

        latch.await();
        executorService.shutdown();
    }

    void insert() {
        tx.executeWithoutResult(status -> {
            Comment prev = null;
            for (int i = 0; i < BULK_INSERT_SIZE; i++) {
                Comment comment = Comment.create(
                        snowflake.nextId(),
                        "content",
                        i % 2 == 0 ? null : prev.getCommentId(),
                        1L,
                        1L
                );
                prev = comment;
                em.persist(comment);
            }
        });
    }
}
