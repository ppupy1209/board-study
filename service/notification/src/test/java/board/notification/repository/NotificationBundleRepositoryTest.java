package board.notification.repository;

import board.notification.model.NotificationBundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationBundleRepositoryTest {
    private static final Long USER_ID = 990_001L;
    private static final Long ARTICLE_ID = 880_001L;

    @Autowired
    NotificationBundleRepository repository;

    @Autowired
    StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("notification:{" + USER_ID + "}:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void groupsReactionsWithinFiveMinutesAndIgnoresDuplicateEvents() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 31, 12, 1);

        for (long index = 1; index <= 100; index++) {
            repository.add(index, USER_ID, ARTICLE_ID, "Popular article", "likeCount", occurredAt.plusSeconds(index));
        }
        for (long index = 101; index <= 120; index++) {
            repository.add(index, USER_ID, ARTICLE_ID, "Popular article", "commentCount", occurredAt.plusSeconds(index));
        }
        repository.add(120L, USER_ID, ARTICLE_ID, "Popular article", "commentCount", occurredAt.plusSeconds(120));

        List<NotificationBundle> notifications = repository.readAll(USER_ID, 20);

        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getLikeCount()).isEqualTo(100);
        assertThat(notifications.getFirst().getCommentCount()).isEqualTo(20);
        assertThat(notifications.getFirst().getEventCount()).isEqualTo(120);
    }

    @Test
    void startsNewBundleAfterFiveMinuteWindow() {
        LocalDateTime firstWindow = LocalDateTime.of(2026, 7, 31, 12, 1);

        repository.add(1L, USER_ID, ARTICLE_ID, "Popular article", "likeCount", firstWindow);
        repository.add(2L, USER_ID, ARTICLE_ID, "Popular article", "likeCount", firstWindow.plusMinutes(5));

        assertThat(repository.readAll(USER_ID, 20)).hasSize(2);
    }
}
