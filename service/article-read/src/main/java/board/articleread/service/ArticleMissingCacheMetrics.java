package board.articleread.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ArticleMissingCacheMetrics {
    private static final String EVENTS = "modu.article.missing.cache.events";
    private static final String RESULT_HIT = "hit";
    private static final String RESULT_STORED = "stored";
    private static final String RESULT_COALESCED = "coalesced";

    private final MeterRegistry meterRegistry;

    public ArticleMissingCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void hit() {
        record(RESULT_HIT);
    }

    public void stored() {
        record(RESULT_STORED);
    }

    public void coalesced() {
        record(RESULT_COALESCED);
    }

    private void record(String result) {
        Counter.builder(EVENTS)
                .description("존재하지 않는 게시글의 부재 캐시 처리 결과")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}