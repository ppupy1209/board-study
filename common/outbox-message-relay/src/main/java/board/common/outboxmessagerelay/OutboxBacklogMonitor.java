package board.common.outboxmessagerelay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 미전송 Outbox backlog 상태를 주기적으로 gauge에 반영한다.
 *
 * <p>Prometheus scrape 시점에 DB를 조회하지 않도록 스케줄러가 값을 미리 채운다.
 * 전용 executor를 사용하는 이유는, backlog가 커져 조회가 느려지더라도
 * shard 할당에 쓰이는 {@link MessageRelayCoordinator#ping()} 스케줄러를 막지 않기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxBacklogMonitor {
    private final OutboxRepository outboxRepository;
    private final MessageRelayMetrics messageRelayMetrics;

    @Scheduled(
            fixedDelay = 5,
            initialDelay = 5,
            timeUnit = TimeUnit.SECONDS,
            scheduler = "messageRelayBacklogMonitorExecutor"
    )
    public void refreshBacklogMetrics() {
        try {
            long pending = outboxRepository.count();
            long oldestAgeSeconds = outboxRepository.findFirstByOrderByCreatedAtAsc()
                    .map(outbox -> Math.max(0, Duration.between(outbox.getCreatedAt(), LocalDateTime.now()).toSeconds()))
                    .orElse(0L);
            messageRelayMetrics.updateBacklog(pending, oldestAgeSeconds);
        } catch (Exception e) {
            log.warn("[OutboxBacklogMonitor.refreshBacklogMetrics] failed to read outbox backlog", e);
        }
    }
}
