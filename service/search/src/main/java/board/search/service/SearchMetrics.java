package board.search.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Component
public class SearchMetrics {
    private final MeterRegistry meterRegistry;
    private final DistributionSummary backendTook;
    private final ConcurrentMap<String, Timer> queryTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> indexCounters = new ConcurrentHashMap<>();

    public SearchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.backendTook = DistributionSummary.builder("modu.search.backend.took.milliseconds")
                .description("Elasticsearch가 보고한 검색 처리 시간")
                .baseUnit("milliseconds")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public <T> T recordQuery(Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            result = "failed";
            throw exception;
        } finally {
            sample.stop(queryTimer(result));
        }
    }

    public void recordBackendTook(long milliseconds) {
        backendTook.record(milliseconds);
    }

    public void recordIndex(String operation, String result, int count) {
        String key = operation + ':' + result;
        indexCounters.computeIfAbsent(key, ignored -> Counter.builder("modu.search.index.operations")
                        .tag("operation", operation)
                        .tag("result", result)
                        .register(meterRegistry))
                .increment(count);
    }

    private Timer queryTimer(String result) {
        return queryTimers.computeIfAbsent(result, ignored -> Timer.builder("modu.search.query")
                .description("게시글 검색 엔진별 실행 시간")
                .tag("engine", "elasticsearch_nori")
                .tag("result", result)
                .publishPercentileHistogram()
                .maximumExpectedValue(Duration.ofSeconds(120))
                .register(meterRegistry));
    }
}
