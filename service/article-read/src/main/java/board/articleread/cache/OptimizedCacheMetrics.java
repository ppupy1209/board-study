package board.articleread.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 논리 TTL 캐시와 Request Collapsing 동작 관측 지표.
 *
 * <p>service label은 management.metrics.tags.service 공통 태그로 자동 부여된다.
 * cache_type은 {@link OptimizedCacheable#type()}에 선언된 고정 문자열만 사용하고,
 * articleId처럼 무한히 증가하는 값은 label에 넣지 않는다.
 */
@Component
public class OptimizedCacheMetrics {
    private static final String REQUESTS = "modu.cache.requests";
    private static final String REFRESH = "modu.cache.refresh";
    private static final String ORIGIN_LOAD = "modu.cache.origin.load";

    /** 논리 TTL이 남아 있어 캐시 데이터를 그대로 반환한 경우. */
    static final String RESULT_HIT = "hit";
    /** 캐시에 데이터가 없어 원본을 조회한 경우. */
    static final String RESULT_MISS = "miss";
    /** 논리 TTL은 만료됐지만 물리 TTL이 남아 있어 기존 데이터를 쓸 수 있는 경우. */
    static final String RESULT_STALE = "stale";

    static final String REFRESH_SUCCESS = "success";
    /** 다른 요청이 이미 갱신 중이라 갱신을 건너뛴 경우. Request Collapsing이 동작한 횟수다. */
    static final String REFRESH_LOCK_LOST = "lock_lost";
    static final String REFRESH_FAILED = "failed";

    private final MeterRegistry meterRegistry;

    public OptimizedCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void request(String cacheType, String result) {
        Counter.builder(REQUESTS)
                .description("논리 TTL 캐시 조회 결과")
                .tag("cache_type", cacheType)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    void refresh(String cacheType, String result) {
        Counter.builder(REFRESH)
                .description("캐시 갱신 시도 결과")
                .tag("cache_type", cacheType)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    void recordOriginLoad(String cacheType, long startNanos) {
        Timer.builder(ORIGIN_LOAD)
                .description("캐시 갱신 시 원본 조회 소요 시간")
                .tag("cache_type", cacheType)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
