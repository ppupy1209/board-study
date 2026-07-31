package board.hotarticle.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class HotArticleReadModelMetrics {
    private static final String REQUESTS = "modu.hot.article.read.model.requests";
    private static final String ORIGIN_CALLS = "modu.hot.article.read.model.origin.calls";

    private final MeterRegistry meterRegistry;

    public HotArticleReadModelMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void hit(long count) {
        record("hit", count);
    }

    public void miss(long count) {
        record("miss", count);
    }

    public void originCall(long count) {
        if (count <= 0) {
            return;
        }
        Counter.builder(ORIGIN_CALLS)
                .description("인기글 조회 모델 누락을 보완하기 위한 Article Service 호출 수")
                .register(meterRegistry)
                .increment(count);
    }

    private void record(String result, long count) {
        if (count <= 0) {
            return;
        }
        Counter.builder(REQUESTS)
                .description("인기글 조회 모델 조회 결과")
                .tag("result", result)
                .register(meterRegistry)
                .increment(count);
    }
}
