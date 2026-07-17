package board.common.outboxmessagerelay;

import board.common.event.payload.EventType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Outbox relay 관측 지표.
 *
 * <p>service label은 management.metrics.tags.service 공통 태그로 모든 지표에 자동 부여되므로 여기서 붙이지 않는다.
 * event_type은 {@link EventType} enum(8개), result는 success/failed 두 값만 사용해 label cardinality를 고정한다.
 * outboxId, payload처럼 무한히 증가하는 값은 label에 넣지 않는다.
 *
 * <p>전송 횟수는 Timer가 제공하는 {@code modu_outbox_publish_seconds_count}로 노출되므로
 * 별도 counter를 두지 않는다. 두 지표가 서로 어긋날 여지를 없애기 위한 선택이다.
 */
@Component
public class MessageRelayMetrics {
    private static final String PUBLISH = "modu.outbox.publish";
    private static final String PENDING_EVENTS = "modu.outbox.pending.events";
    private static final String OLDEST_EVENT = "modu.outbox.oldest.event";

    static final String RESULT_SUCCESS = "success";
    static final String RESULT_FAILED = "failed";

    private final MeterRegistry meterRegistry;
    private final AtomicLong pendingEvents = new AtomicLong();
    private final AtomicLong oldestEventAgeSeconds = new AtomicLong();

    public MessageRelayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder(PENDING_EVENTS, pendingEvents, AtomicLong::doubleValue)
                .description("Kafka로 아직 전송되지 못한 Outbox 행 수")
                .register(meterRegistry);

        Gauge.builder(OLDEST_EVENT, oldestEventAgeSeconds, AtomicLong::doubleValue)
                .description("가장 오래된 미전송 Outbox 행의 경과 시간")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    void publishSucceeded(EventType eventType, long startNanos) {
        record(eventType, RESULT_SUCCESS, startNanos);
    }

    void publishFailed(EventType eventType, long startNanos) {
        record(eventType, RESULT_FAILED, startNanos);
    }

    private void record(EventType eventType, String result, long startNanos) {
        Timer.builder(PUBLISH)
                .description("Outbox 이벤트 Kafka 전송 시도")
                .tag("event_type", eventType.name())
                .tag("result", result)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    void updateBacklog(long pending, long oldestAgeSeconds) {
        pendingEvents.set(pending);
        oldestEventAgeSeconds.set(oldestAgeSeconds);
    }
}
