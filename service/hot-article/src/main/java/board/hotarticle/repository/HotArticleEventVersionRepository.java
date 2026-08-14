package board.hotarticle.repository;

import board.hotarticle.kafka.HotArticleEventPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class HotArticleEventVersionRepository {
    private static final String VERSION_KEY_FORMAT = "hot-article::event-version::%s::%s::%s";
    private static final String LOCK_KEY_FORMAT = "hot-article::event-version-lock::%s::%s::%s";
    static final Duration VERSION_TTL = Duration.ofDays(2);
    static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(HotArticleEventPosition position, Long articleId, String owner) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                lockKey(position, articleId),
                owner,
                LOCK_TTL
        ));
    }

    public Long readLatestOffset(HotArticleEventPosition position, Long articleId) {
        String offset = redisTemplate.opsForValue().get(versionKey(position, articleId));
        return offset == null ? null : Long.valueOf(offset);
    }

    public void saveLatestOffset(HotArticleEventPosition position, Long articleId) {
        redisTemplate.opsForValue().set(
                versionKey(position, articleId),
                String.valueOf(position.offset()),
                VERSION_TTL
        );
    }

    public void unlock(HotArticleEventPosition position, Long articleId, String owner) {
        redisTemplate.execute(
                RELEASE_LOCK,
                List.of(lockKey(position, articleId)),
                owner
        );
    }

    private String versionKey(HotArticleEventPosition position, Long articleId) {
        return VERSION_KEY_FORMAT.formatted(position.topic(), position.partition(), articleId);
    }

    private String lockKey(HotArticleEventPosition position, Long articleId) {
        return LOCK_KEY_FORMAT.formatted(position.topic(), position.partition(), articleId);
    }
}
