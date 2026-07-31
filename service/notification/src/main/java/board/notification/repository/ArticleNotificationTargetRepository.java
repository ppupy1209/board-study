package board.notification.repository;

import board.common.dataserializer.DataSerializer;
import board.notification.model.ArticleNotificationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ArticleNotificationTargetRepository {
    private static final Duration TARGET_TTL = Duration.ofDays(30);
    private final StringRedisTemplate redisTemplate;

    public void save(ArticleNotificationTarget target) {
        redisTemplate.opsForValue().set(key(target.getArticleId()), DataSerializer.serialize(target), TARGET_TTL);
    }

    public Optional<ArticleNotificationTarget> read(Long articleId) {
        String value = redisTemplate.opsForValue().get(key(articleId));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DataSerializer.deserialize(value, ArticleNotificationTarget.class));
    }

    public void delete(Long articleId) {
        redisTemplate.delete(key(articleId));
    }

    private String key(Long articleId) {
        return "notification:article:" + articleId;
    }
}
