package board.view.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ArticleViewCountRepository {
    private final StringRedisTemplate redisTemplate;
    private final ArticleViewCountBackUpRepository backUpRepository;

    private static final String KEY_FORMAT = "view::article::%s::view_count";

    public Long read(Long articleId) {
        String key = generateKey(articleId);
        String result = redisTemplate.opsForValue().get(key);
        if (result != null) {
            return Long.valueOf(result);
        }
        Long storedCount = backUpRepository.findById(articleId)
                .map(articleViewCount -> articleViewCount.getViewCount())
                .orElse(0L);
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(storedCount));
        return storedCount;
    }

    /**
     * 여러 게시글의 조회수를 한 번에 읽는다 (Redis MGET 1회).
     *
     * <p>목록 조회는 게시글 30건의 조회수를 한꺼번에 필요로 한다. 건당 GET을 30번 하면
     * 왕복이 30번 생긴다. article-read의 목록 팬아웃을 없애기 위해 추가했다.
     */
    public Map<Long, Long> readAll(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = articleIds.stream().map(this::generateKey).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        Map<Long, Long> result = new HashMap<>();
        List<Long> missingIds = new java.util.ArrayList<>();
        for (int i = 0; i < articleIds.size(); i++) {
            String value = (values == null || i >= values.size()) ? null : values.get(i);
            if (value == null) {
                missingIds.add(articleIds.get(i));
            } else {
                result.put(articleIds.get(i), Long.valueOf(value));
            }
        }
        Map<Long, Long> storedCounts = backUpRepository.findAllById(missingIds).stream()
                .collect(Collectors.toMap(
                        articleViewCount -> articleViewCount.getArticleId(),
                        articleViewCount -> articleViewCount.getViewCount()
                ));
        for (Long articleId : missingIds) {
            Long count = storedCounts.getOrDefault(articleId, 0L);
            redisTemplate.opsForValue().setIfAbsent(generateKey(articleId), String.valueOf(count));
            result.put(articleId, count);
        }
        return result;
    }

    public Long increase(Long articleId) {
        String key = generateKey(articleId);
        if (redisTemplate.opsForValue().get(key) == null) {
            Long storedCount = backUpRepository.findById(articleId)
                    .map(articleViewCount -> articleViewCount.getViewCount())
                    .orElse(0L);
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(storedCount));
        }
        return redisTemplate.opsForValue().increment(key);
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
