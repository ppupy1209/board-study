package board.hotarticle.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

public class HotArticleDlqMetrics {
    private static final String DELIVERY = "modu.kafka.delivery";
    private static final String REPLAY = "modu.kafka.dlq.replay";
    private static final String REPLAY_BATCH = "modu.kafka.dlq.replay.batch";

    private final MeterRegistry meterRegistry;

    public HotArticleDlqMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRetry(String topic, int attempt) {
        if (attempt > 1) {
            delivery(topic, "retry").increment();
        }
    }

    public void recordDlq(String topic) {
        delivery(topic, "dlq").increment();
    }

    public void recordDlqPublishFailure(String topic) {
        delivery(topic, "dlq_publish_failed").increment();
    }

    public void recordReplayBatch(int size) {
        DistributionSummary.builder(REPLAY_BATCH)
                .description("한 poll에서 가져온 DLQ 재처리 건수")
                .register(meterRegistry)
                .record(size);
    }

    public void recordReplay(String topic, String result) {
        Counter.builder(REPLAY)
                .description("DLQ 이벤트 재처리 결과")
                .tag("topic", topic)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private Counter delivery(String topic, String result) {
        return Counter.builder(DELIVERY)
                .description("Kafka Consumer 재시도와 DLQ 격리 결과")
                .tag("topic", topic)
                .tag("result", result)
                .register(meterRegistry);
    }
}
