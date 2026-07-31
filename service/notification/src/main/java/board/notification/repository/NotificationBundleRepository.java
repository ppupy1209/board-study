package board.notification.repository;

import board.notification.model.NotificationBundle;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class NotificationBundleRepository {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DefaultRedisScript<Long> ADD_TO_BUNDLE = new DefaultRedisScript<>("""
            if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1]) == false then
                return 0
            end
            redis.call('HSET', KEYS[2],
                'articleId', ARGV[2],
                'title', ARGV[3],
                'updatedAt', ARGV[4])
            redis.call('HINCRBY', KEYS[2], ARGV[5], 1)
            redis.call('EXPIRE', KEYS[2], ARGV[1])
            redis.call('ZADD', KEYS[3], ARGV[4], ARGV[6])
            redis.call('EXPIRE', KEYS[3], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${notification.bundle-window:5m}")
    private Duration bundleWindow;

    @Value("${notification.retention:7d}")
    private Duration retention;

    public boolean add(
            Long eventId,
            Long recipientId,
            Long articleId,
            String title,
            String countField,
            LocalDateTime occurredAt
    ) {
        long occurredAtMillis = occurredAt.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
        long windowMillis = bundleWindow.toMillis();
        long windowStartMillis = occurredAtMillis / windowMillis * windowMillis;
        String notificationId = articleId + ":" + windowStartMillis;
        String hashTag = "{" + recipientId + "}";

        Long result = redisTemplate.execute(
                ADD_TO_BUNDLE,
                List.of(
                        "notification:" + hashTag + ":event:" + eventId,
                        "notification:" + hashTag + ":bundle:" + notificationId,
                        "notification:" + hashTag + ":index"
                ),
                String.valueOf(retention.toSeconds()),
                String.valueOf(articleId),
                title,
                String.valueOf(occurredAtMillis),
                countField,
                notificationId
        );
        return Long.valueOf(1L).equals(result);
    }

    public List<NotificationBundle> readAll(Long recipientId, int limit) {
        String hashTag = "{" + recipientId + "}";
        Set<String> notificationIds = redisTemplate.opsForZSet().reverseRange(
                "notification:" + hashTag + ":index",
                0,
                Math.max(0, limit - 1)
        );
        if (notificationIds == null || notificationIds.isEmpty()) {
            return List.of();
        }

        List<NotificationBundle> result = new ArrayList<>();
        for (String notificationId : notificationIds) {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(
                    "notification:" + hashTag + ":bundle:" + notificationId
            );
            if (!values.isEmpty()) {
                result.add(NotificationBundle.from(notificationId, values));
            }
        }
        return result;
    }
}
