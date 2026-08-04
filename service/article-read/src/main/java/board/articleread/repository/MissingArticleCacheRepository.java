package board.articleread.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Repository
@RequiredArgsConstructor
public class MissingArticleCacheRepository {
    private static final String KEY_FORMAT = "article-read::missing-article::%s";
    private static final String MISSING_VALUE = "MISSING";
    static final long BASE_TTL_SECONDS = 60;
    static final long TTL_JITTER_SECONDS = 10;

    private final StringRedisTemplate redisTemplate;

    public boolean isMissing(Long articleId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateKey(articleId)));
    }

    public void markMissing(Long articleId) {
        redisTemplate.opsForValue().set(
                generateKey(articleId),
                MISSING_VALUE,
                nextTtl()
        );
    }

    public void delete(Long articleId) {
        redisTemplate.delete(generateKey(articleId));
    }

    Duration nextTtl() {
        long jitter = ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS + 1);
        return Duration.ofSeconds(BASE_TTL_SECONDS + jitter);
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}