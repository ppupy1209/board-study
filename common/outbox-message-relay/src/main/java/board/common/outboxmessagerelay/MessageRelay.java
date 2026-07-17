package board.common.outboxmessagerelay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay {
    private final OutboxRepository outboxRepository;
    private final MessageRelayCoordinator messageRelayCoordinator;
    private final KafkaTemplate<String, String> messageRelayKafkaTemplate;
    private final MessageRelayMetrics messageRelayMetrics;

    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 1;

    /** 한 주기에 shard당 재발행할 최대 건수. 복구 속도와 장애 중 주기 길이의 트레이드오프다. */
    @Value("${modu.outbox.relay.publish-pending-batch-size:500}")
    private int pendingBatchSize;

    /** 이 시간이 지나도 남아 있는 Outbox만 재발행 대상으로 본다. 커밋 직후 비동기 발행에 기회를 준다. */
    @Value("${modu.outbox.relay.pending-age-threshold-seconds:10}")
    private long pendingAgeThresholdSeconds;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createOutbox(OutboxEvent outboxEvent) {
        log.info("[MessageRelay.createOutbox] outboxEvent = {}", outboxEvent);
        outboxRepository.save(outboxEvent.getOutbox());
    }

    @Async("messageRelayPublishEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishEvent(OutboxEvent outboxEvent) {
        publishEvent(outboxEvent.getOutbox());
    }

    /**
     * Kafka 전송이 확인된 경우에만 Outbox를 삭제한다.
     *
     * <p>전송에 실패하면 행을 그대로 남겨 {@link #publishPendingEvent()}의 재시도 대상이 되게 한다.
     * 따라서 Kafka 장애 중에도 이벤트가 유실되지 않는다. 대신 전송은 성공했으나 응답 확인 전에
     * 타임아웃이 난 경우 같은 이벤트가 중복 전송될 수 있으며(at-least-once), 이는 consumer 멱등성으로 처리한다.
     */
    private void publishEvent(Outbox outbox) {
        long startNanos = System.nanoTime();
        try {
            messageRelayKafkaTemplate.send(
                    outbox.getEventType().getTopic(),
                    String.valueOf(outbox.getShardKey()),
                    outbox.getPayload()
            ).get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outboxRepository.delete(outbox);
            messageRelayMetrics.publishSucceeded(outbox.getEventType(), startNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[MessageRelay.publishEvent] interrupted, keeping outbox for retry. outbox = {}", outbox, e);
            messageRelayMetrics.publishFailed(outbox.getEventType(), startNanos);
        } catch (Exception e) {
            log.error("[MessageRelay.publishEvent] failed, keeping outbox for retry. outbox = {}", outbox, e);
            messageRelayMetrics.publishFailed(outbox.getEventType(), startNanos);
        }
    }

    /**
     * 아직 전송되지 못한 Outbox를 다시 발행한다.
     *
     * <p>한 주기에 shard당 {@code pendingBatchSize}건까지만 처리하므로 재시도 처리율에 상한이 있다.
     * 상한 = (shard 수 × batch size) / interval.
     *
     * <p>이 값이 장애 복구 시간을 결정한다. 실측에서 batch=100 / interval=10s일 때
     * backlog 603건이 0으로 수렴하는 데 183초가 걸려 목표(120초)를 넘겼다.
     * 그때 이벤트가 shard 하나에 몰려 있었기 때문에 실효 처리율이 100건/10초 = 10건/s였다.
     * (article 이벤트의 shardKey는 boardId라, 게시판이 적으면 특정 shard로 쏠린다.)
     *
     * <p>batch size를 키우면 복구가 빨라지지만, Kafka가 완전히 죽은 동안에는 한 주기가
     * (batch size × 전송 타임아웃)까지 길어질 수 있다. 이 스케줄러는 전용 단일 스레드라
     * 다른 경로를 막지는 않지만, 그만큼 재시도 자체가 늦어진다. 그래서 값을 고정하지 않고 설정으로 뺀다.
     */
    @Scheduled(
            fixedDelayString = "${modu.outbox.relay.publish-pending-interval-seconds:10}",
            initialDelay = 5,
            timeUnit = TimeUnit.SECONDS,
            scheduler = "messageRelayPublishPendingExecutor"
    )
    public void publishPendingEvent() {
        AssignedShard assignedShard = messageRelayCoordinator.assignedShards();
        log.info("[MessageRelay.publishPendingEvent] assignedShard size = {}]", assignedShard.getShards().size());
        for (Long shard : assignedShard.getShards()) {
            List<Outbox> outboxes = outboxRepository.findAllByShardKeyAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                    shard,
                    LocalDateTime.now().minusSeconds(pendingAgeThresholdSeconds),
                    Pageable.ofSize(pendingBatchSize)
            );
            for (Outbox outbox : outboxes) {
                publishEvent(outbox);
            }
        }
    }

}
