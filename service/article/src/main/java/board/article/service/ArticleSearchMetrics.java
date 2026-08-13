package board.article.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Component
public class ArticleSearchMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

    public ArticleSearchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T record(String engine, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            result = "failed";
            throw exception;
        } finally {
            sample.stop(timer(engine, result));
        }
    }

    private Timer timer(String engine, String result) {
        String key = engine + ':' + result;
        return timers.computeIfAbsent(key, ignored -> Timer.builder("modu.search.query")
                .description("게시글 검색 엔진별 실행 시간")
                .tag("engine", engine)
                .tag("result", result)
                .publishPercentileHistogram()
                .maximumExpectedValue(Duration.ofSeconds(120))
                .register(meterRegistry));
    }
}
