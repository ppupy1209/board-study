package board.articleread.cache;

import board.common.dataserializer.DataSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static board.articleread.cache.OptimizedCacheMetrics.*;
import static java.util.stream.Collectors.joining;

@Component
@RequiredArgsConstructor
public class OptimizedCacheManager {
    private final StringRedisTemplate redisTemplate;
    private final OptimizedCacheLockProvider optimizedCacheLockProvider;
    private final OptimizedCacheMetrics optimizedCacheMetrics;

    private static final String DELIMITER = "::";

    public Object process(String type, long ttlSeconds, Object[] args, Class<?> returnType,
                          OptimizedCacheOriginDataSupplier<?> originDataSupplier) throws Throwable {
        String key = generateKey(type, args);

        String cachedData = redisTemplate.opsForValue().get(key);
        if (cachedData == null) {
            optimizedCacheMetrics.request(type, RESULT_MISS);
            return refresh(originDataSupplier, type, key, ttlSeconds);
        }

        OptimizedCache optimizedCache = DataSerializer.deserialize(cachedData, OptimizedCache.class);
        if (optimizedCache == null) {
            optimizedCacheMetrics.request(type, RESULT_MISS);
            return refresh(originDataSupplier, type, key, ttlSeconds);
        }

        if (!optimizedCache.isExpired()) {
            optimizedCacheMetrics.request(type, RESULT_HIT);
            return optimizedCache.parseData(returnType);
        }

        optimizedCacheMetrics.request(type, RESULT_STALE);

        if (!optimizedCacheLockProvider.lock(key)) {
            // 다른 요청이 이미 갱신 중이므로 원본을 다시 조회하지 않고 기존 데이터를 반환한다.
            // 이 counter의 증가량이 Request Collapsing으로 막아낸 원본 조회 횟수다.
            optimizedCacheMetrics.refresh(type, REFRESH_LOCK_LOST);
            return optimizedCache.parseData(returnType);
        }

        try {
            return refresh(originDataSupplier, type, key, ttlSeconds);
        } finally {
            optimizedCacheLockProvider.unlock(key);
        }
    }

    private Object refresh(OptimizedCacheOriginDataSupplier<?> originDataSupplier, String type, String key, long ttlSeconds) throws Throwable {
        long startNanos = System.nanoTime();
        Object result;
        try {
            result = originDataSupplier.get();
        } catch (Throwable e) {
            optimizedCacheMetrics.refresh(type, REFRESH_FAILED);
            throw e;
        }
        optimizedCacheMetrics.recordOriginLoad(type, startNanos);

        OptimizedCacheTTL optimizedCacheTTL = OptimizedCacheTTL.of(ttlSeconds);
        OptimizedCache optimizedCache = OptimizedCache.of(result, optimizedCacheTTL.getLogicalTTL());

        redisTemplate.opsForValue()
                .set(
                        key,
                        DataSerializer.serialize(optimizedCache),
                        optimizedCacheTTL.getPhysicalTTL()
                );

        optimizedCacheMetrics.refresh(type, REFRESH_SUCCESS);
        return result;
    }

    private String generateKey(String prefix, Object[] args) {
        return prefix + DELIMITER +
                Arrays.stream(args)
                        .map(String::valueOf)
                        .collect(joining(DELIMITER));
    }
}
