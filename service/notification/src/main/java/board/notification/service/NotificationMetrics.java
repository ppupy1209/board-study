package board.notification.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {
    private final MeterRegistry meterRegistry;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String result) {
        Counter.builder("modu.notification.event")
                .description("Notification event processing result")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
