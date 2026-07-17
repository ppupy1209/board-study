package board.articleread.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Query Model(조회 모델) 적중률과 원본 팬아웃 관측 지표.
 *
 * <p>이 지표가 없어서 문제를 늦게 발견했다. 조회 모델이 거의 적중하지 않아 상세 조회 1건이
 * 원본 호출 4건으로 번지고 있었는데, soak test로 부하를 크게 올리고 나서야 드러났다.
 * 적중률을 처음부터 노출했다면 smoke 단계에서 바로 보였을 것이다.
 *
 * <p>service label은 management.metrics.tags.service 공통 태그로 자동 부여된다.
 * result는 hit/miss 두 값만 사용하고 articleId 같은 값은 label에 넣지 않는다.
 */
@Component
public class QueryModelMetrics {
    private static final String REQUESTS = "modu.query.model.requests";
    private static final String ORIGIN_CALLS = "modu.query.model.origin.calls";

    static final String RESULT_HIT = "hit";
    static final String RESULT_MISS = "miss";

    private final MeterRegistry meterRegistry;

    public QueryModelMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 조회 모델을 찾아본 결과. hit이면 원본을 부르지 않는다. */
    void record(String result, long count) {
        if (count <= 0) {
            return;
        }
        Counter.builder(REQUESTS)
                .description("조회 모델 적중 여부. miss면 원본 서비스를 호출해야 한다.")
                .tag("result", result)
                .register(meterRegistry)
                .increment(count);
    }

    void hit(long count) {
        record(RESULT_HIT, count);
    }

    void miss(long count) {
        record(RESULT_MISS, count);
    }

    /**
     * miss를 메우려고 원본 서비스를 부른 횟수.
     * 이 값이 요청 수보다 훨씬 크면 조회 모델이 부하를 줄이는 게 아니라 늘리고 있다는 뜻이다.
     */
    void originCall(String target, long count) {
        if (count <= 0) {
            return;
        }
        Counter.builder(ORIGIN_CALLS)
                .description("조회 모델 miss를 메우기 위한 원본 서비스 호출 수")
                .tag("target", target)
                .register(meterRegistry)
                .increment(count);
    }
}
