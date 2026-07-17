package board.common.outboxmessagerelay;

import board.common.event.payload.EventType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox는 Kafka 전송이 확인된 경우에만 삭제되어야 한다.
 * 전송에 실패했는데도 삭제하면 Kafka 장애 구간의 이벤트가 그대로 유실되므로,
 * 성공/예외/타임아웃/재시도 성공 네 가지 경로를 테스트로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class MessageRelayTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private MessageRelayCoordinator messageRelayCoordinator;
    @Mock
    private KafkaTemplate<String, String> messageRelayKafkaTemplate;

    private MeterRegistry meterRegistry;
    private MessageRelay messageRelay;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        messageRelay = new MessageRelay(
                outboxRepository,
                messageRelayCoordinator,
                messageRelayKafkaTemplate,
                new MessageRelayMetrics(meterRegistry)
        );
        // @Value 필드는 스프링 컨텍스트 없이 생성하면 0이 되어 Pageable.ofSize(0)에서 터진다.
        // 운영 기본값과 같은 값을 넣어 준다.
        ReflectionTestUtils.setField(messageRelay, "pendingBatchSize", 500);
        ReflectionTestUtils.setField(messageRelay, "pendingAgeThresholdSeconds", 10L);
    }

    private Outbox outbox() {
        return Outbox.create(1L, EventType.ARTICLE_CREATED, "{\"articleId\":1}", 3L);
    }

    private void givenKafkaSendSucceeds() {
        when(messageRelayKafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    private void givenKafkaSendFailsWith(Throwable cause) {
        when(messageRelayKafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(cause));
    }

    /** 1초 전송 타임아웃을 유발하기 위해 끝나지 않는 future를 반환한다. */
    private void givenKafkaSendNeverCompletes() {
        when(messageRelayKafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());
    }

    private double publishCount(String result) {
        return meterRegistry.find("modu.outbox.publish")
                .tag("event_type", EventType.ARTICLE_CREATED.name())
                .tag("result", result)
                .timers().stream()
                .mapToDouble(timer -> timer.count())
                .sum();
    }

    @Test
    @DisplayName("Kafka 전송이 성공하면 Outbox를 삭제하고 success로 집계한다")
    void deletesOutboxOnlyAfterSuccessfulSend() {
        Outbox outbox = outbox();
        givenKafkaSendSucceeds();

        messageRelay.publishEvent(OutboxEvent.of(outbox));

        verify(outboxRepository).delete(outbox);
        assertThat(publishCount(MessageRelayMetrics.RESULT_SUCCESS)).isEqualTo(1);
        assertThat(publishCount(MessageRelayMetrics.RESULT_FAILED)).isZero();
    }

    @Test
    @DisplayName("Kafka 전송이 예외로 실패하면 Outbox를 남기고 failed로 집계한다")
    void keepsOutboxWhenSendThrows() {
        Outbox outbox = outbox();
        givenKafkaSendFailsWith(new IllegalStateException("broker down"));

        messageRelay.publishEvent(OutboxEvent.of(outbox));

        verify(outboxRepository, never()).delete(any());
        assertThat(publishCount(MessageRelayMetrics.RESULT_FAILED)).isEqualTo(1);
        assertThat(publishCount(MessageRelayMetrics.RESULT_SUCCESS)).isZero();
    }

    @Test
    @DisplayName("Kafka 전송이 타임아웃되면 Outbox를 남기고 failed로 집계한다")
    void keepsOutboxWhenSendTimesOut() {
        Outbox outbox = outbox();
        givenKafkaSendNeverCompletes();

        messageRelay.publishEvent(OutboxEvent.of(outbox));

        verify(outboxRepository, never()).delete(any());
        assertThat(publishCount(MessageRelayMetrics.RESULT_FAILED)).isEqualTo(1);
    }

    @Test
    @DisplayName("전송 실패로 남은 Outbox는 재시도 스케줄러가 다시 보내고 성공하면 삭제한다")
    void retriesPendingOutboxAndDeletesAfterSuccess() {
        Outbox outbox = outbox();

        // 1회차: 장애로 실패 -> 삭제되지 않고 남는다
        givenKafkaSendFailsWith(new IllegalStateException("broker down"));
        messageRelay.publishEvent(OutboxEvent.of(outbox));
        verify(outboxRepository, never()).delete(any());

        // 2회차: 재시도 스케줄러가 남은 Outbox를 집어 다시 전송하고, 이번엔 성공한다
        when(messageRelayCoordinator.assignedShards()).thenReturn(AssignedShard.of("app", List.of("app"), 4));
        when(outboxRepository.findAllByShardKeyAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                anyLong(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(outbox), List.of(), List.of(), List.of());
        givenKafkaSendSucceeds();

        messageRelay.publishPendingEvent();

        verify(outboxRepository, times(1)).delete(outbox);
        assertThat(publishCount(MessageRelayMetrics.RESULT_SUCCESS)).isEqualTo(1);
        assertThat(publishCount(MessageRelayMetrics.RESULT_FAILED)).isEqualTo(1);
    }
}
