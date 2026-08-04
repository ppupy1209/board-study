package board.articleread.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ArticleLookupLockRepository {
    private static final String KEY_FORMAT = "article-read::article-lookup-lock::%s";
    static final Duration LOCK_TTL = Duration.ofSeconds(3);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(Long articleId, String owner) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(generateKey(articleId), owner, LOCK_TTL)
        );
    }

    public void release(Long articleId, String owner) {
        redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(generateKey(articleId)),
                owner
        );
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
