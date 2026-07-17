package board.common.event;

import board.common.event.payload.EventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Kafka consumer의 이벤트 처리 관측 지표. article-read와 hot-article이 함께 사용한다.
 *
 * <p>service label은 각 서비스의 management.metrics.tags.service 공통 태그로 자동 부여된다.
 * event_type은 {@link EventType} enum(8개)과 파싱 실패를 뜻하는 unknown으로 제한되고,
 * result는 success/failed/ignored 세 값만 사용해 label cardinality를 고정한다.
 * articleId나 payload 본문은 label에 넣지 않는다.
 *
 * <h2>result="duplicate"를 두지 않은 이유</h2>
 * Outbox 재시도는 같은 이벤트를 중복 전송할 수 있다(at-least-once). 그러나 Query Model 핸들러는
 * payload가 실어 보낸 <b>절대값</b>을 그대로 반영하고(예: {@code createOrUpdate(articleId, payload.getArticleLikeCount())})
 * 증분 연산을 하지 않는다. 따라서 같은 이벤트를 몇 번 소비해도 결과가 동일하며, 중복을 판별하기 위한
 * 별도 dedup 저장소가 없다. 지표 label 하나를 위해 dedup 인프라를 새로 만들지 않고,
 * 중복 반영이 없다는 사실은 멱등성 테스트로 검증한다.
 *
 * <h2>modu_query_model_lag_seconds의 정의</h2>
 * Kafka record timestamp와 소비 완료 시각의 차이다. 즉 <b>Kafka에 기록된 이후</b>부터 Query Model에
 * 반영되기까지의 지연만 포함한다. Kafka 장애로 Outbox에 머문 시간은 포함되지 않으므로,
 * 장애 구간의 지연은 {@code modu_outbox_oldest_event_seconds}로 함께 봐야 한다.
 */
public class EventConsumeMetrics {
    private static final String CONSUME = "modu.event.consume";
    private static final String PROCESSING = "modu.event.processing";
    private static final String QUERY_MODEL_LAG = "modu.query.model.lag";

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILED = "failed";
    /** 파싱에 실패했거나 이 서비스가 처리하지 않는 이벤트. */
    public static final String RESULT_IGNORED = "ignored";

    private static final String UNKNOWN_EVENT_TYPE = "unknown";

    private final MeterRegistry meterRegistry;

    public EventConsumeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess(EventType eventType, long startNanos, long recordTimestampMillis) {
        String type = tagOf(eventType);
        count(type, RESULT_SUCCESS);
        recordProcessing(type, startNanos);
        recordQueryModelLag(type, recordTimestampMillis);
    }

    public void recordFailure(EventType eventType, long startNanos) {
        String type = tagOf(eventType);
        count(type, RESULT_FAILED);
        recordProcessing(type, startNanos);
    }

    public void recordIgnored(EventType eventType) {
        count(tagOf(eventType), RESULT_IGNORED);
    }

    private String tagOf(EventType eventType) {
        return eventType == null ? UNKNOWN_EVENT_TYPE : eventType.name();
    }

    private void count(String eventType, String result) {
        Counter.builder(CONSUME)
                .description("Kafka 이벤트 소비 결과")
                .tag("event_type", eventType)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private void recordProcessing(String eventType, long startNanos) {
        Timer.builder(PROCESSING)
                .description("이벤트 수신 후 처리 완료까지 소요 시간")
                .tag("event_type", eventType)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private void recordQueryModelLag(String eventType, long recordTimestampMillis) {
        if (recordTimestampMillis <= 0) {
            return;
        }
        long lagMillis = Math.max(0, System.currentTimeMillis() - recordTimestampMillis);
        Timer.builder(QUERY_MODEL_LAG)
                .description("Kafka record 기록 시각부터 Query Model 반영까지의 지연")
                .tag("event_type", eventType)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofMinutes(1))
                .register(meterRegistry)
                .record(lagMillis, TimeUnit.MILLISECONDS);
    }
}
