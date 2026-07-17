package board.articleread.cache;

import board.common.dataserializer.DataSerializer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 논리 TTL 캐시의 hit / miss / stale 판정과 Request Collapsing 동작을 지표로 고정한다.
 *
 * <p>특히 lock_lost는 "논리 TTL이 만료된 동일 key에 요청이 몰려도 원본 조회는 한 번만 나간다"는
 * Request Collapsing 효과의 근거이므로 반드시 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OptimizedCacheManagerTest {

    private static final String CACHE_TYPE = "articleViewCount";
    private static final String KEY = CACHE_TYPE + "::1";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private OptimizedCacheLockProvider optimizedCacheLockProvider;

    private MeterRegistry meterRegistry;
    private OptimizedCacheManager optimizedCacheManager;
    private AtomicInteger originCallCount;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        originCallCount = new AtomicInteger();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        optimizedCacheManager = new OptimizedCacheManager(
                redisTemplate,
                optimizedCacheLockProvider,
                new OptimizedCacheMetrics(meterRegistry)
        );
    }

    private Object process() throws Throwable {
        return optimizedCacheManager.process(
                CACHE_TYPE, 10, new Object[]{1L}, Long.class,
                () -> {
                    originCallCount.incrementAndGet();
                    return 42L;
                }
        );
    }

    private void givenCached(Duration logicalTtl) {
        when(valueOperations.get(KEY))
                .thenReturn(DataSerializer.serialize(OptimizedCache.of(42L, logicalTtl)));
    }

    private double counter(String name, String result) {
        Counter c = meterRegistry.find(name).tag("cache_type", CACHE_TYPE).tag("result", result).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    @DisplayName("캐시에 데이터가 없으면 miss로 집계하고 원본을 조회해 채운다")
    void recordsMissAndLoadsOrigin() throws Throwable {
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(process()).isEqualTo(42L);

        assertThat(originCallCount).hasValue(1);
        assertThat(counter("modu.cache.requests", "miss")).isEqualTo(1);
        assertThat(counter("modu.cache.refresh", "success")).isEqualTo(1);
        assertThat(meterRegistry.find("modu.cache.origin.load").tag("cache_type", CACHE_TYPE).timer().count()).isEqualTo(1);
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("논리 TTL이 남아 있으면 hit으로 집계하고 원본을 조회하지 않는다")
    void recordsHitWithoutOriginLoad() throws Throwable {
        givenCached(Duration.ofSeconds(30));

        assertThat(process()).isEqualTo(42L);

        assertThat(originCallCount).hasValue(0);
        assertThat(counter("modu.cache.requests", "hit")).isEqualTo(1);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("논리 TTL이 만료되고 lock을 잡으면 stale로 집계한 뒤 갱신한다")
    void recordsStaleAndRefreshesWhenLockAcquired() throws Throwable {
        givenCached(Duration.ofSeconds(-30));
        when(optimizedCacheLockProvider.lock(KEY)).thenReturn(true);

        assertThat(process()).isEqualTo(42L);

        assertThat(originCallCount).hasValue(1);
        assertThat(counter("modu.cache.requests", "stale")).isEqualTo(1);
        assertThat(counter("modu.cache.refresh", "success")).isEqualTo(1);
        verify(optimizedCacheLockProvider).unlock(KEY);
    }

    @Test
    @DisplayName("논리 TTL이 만료됐지만 다른 요청이 갱신 중이면 lock_lost로 집계하고 원본을 조회하지 않는다")
    void recordsLockLostAndSkipsOriginLoad() throws Throwable {
        givenCached(Duration.ofSeconds(-30));
        when(optimizedCacheLockProvider.lock(KEY)).thenReturn(false);

        assertThat(process()).isEqualTo(42L);

        assertThat(originCallCount).hasValue(0);
        assertThat(counter("modu.cache.requests", "stale")).isEqualTo(1);
        assertThat(counter("modu.cache.refresh", "lock_lost")).isEqualTo(1);
        assertThat(counter("modu.cache.refresh", "success")).isZero();
        verify(optimizedCacheLockProvider, never()).unlock(KEY);
    }

    @Test
    @DisplayName("논리 TTL 만료 후 동일 key에 요청이 몰려도 원본 조회는 lock을 잡은 한 요청만 수행한다")
    void collapsesConcurrentRefreshRequestsIntoSingleOriginLoad() throws Throwable {
        givenCached(Duration.ofSeconds(-30));
        // 첫 요청만 lock 획득에 성공하고 나머지는 실패하는 상황을 재현한다.
        when(optimizedCacheLockProvider.lock(KEY)).thenReturn(true, false, false, false, false);

        for (int i = 0; i < 5; i++) {
            assertThat(process()).isEqualTo(42L);
        }

        assertThat(originCallCount).hasValue(1);
        assertThat(counter("modu.cache.requests", "stale")).isEqualTo(5);
        assertThat(counter("modu.cache.refresh", "success")).isEqualTo(1);
        assertThat(counter("modu.cache.refresh", "lock_lost")).isEqualTo(4);
    }

    @Test
    @DisplayName("원본 조회가 실패하면 failed로 집계하고 예외를 그대로 전파한다")
    void recordsFailedWhenOriginThrows() {
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                optimizedCacheManager.process(CACHE_TYPE, 10, new Object[]{1L}, Long.class, () -> {
                    throw new IllegalStateException("origin down");
                })
        )).isInstanceOf(IllegalStateException.class);

        assertThat(counter("modu.cache.refresh", "failed")).isEqualTo(1);
        assertThat(counter("modu.cache.refresh", "success")).isZero();
    }
}
