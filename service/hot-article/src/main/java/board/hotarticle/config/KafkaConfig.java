package board.hotarticle.config;

import board.common.event.EventConsumeMetrics;
import board.hotarticle.kafka.HotArticleDlqMetrics;
import board.hotarticle.kafka.HotArticleKafkaTopics;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    private static final long DEFAULT_MAX_POLL_INTERVAL_MILLIS = 300_000L;

    /** common:event 모듈의 클래스라 컴포넌트 스캔 대상이 아니므로 사용하는 서비스가 직접 등록한다. */
    @Bean
    public EventConsumeMetrics eventConsumeMetrics(MeterRegistry meterRegistry) {
        return new EventConsumeMetrics(meterRegistry);
    }

    @Bean
    public HotArticleDlqMetrics hotArticleDlqMetrics(MeterRegistry meterRegistry) {
        return new HotArticleDlqMetrics(meterRegistry);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            HotArticleDlqMetrics dlqMetrics,
            @Value("${modu.kafka.failure.retry-interval-ms:1000}") long retryIntervalMillis,
            @Value("${modu.kafka.failure.max-attempts:3}") long maxAttempts
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        HotArticleKafkaTopics.dlqTopic(record.topic()),
                        -1
                )
        );
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(5));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryIntervalMillis, Math.max(0, maxAttempts - 1))
        );
        errorHandler.setCommitRecovered(true);
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                       Exception exception,
                                       int deliveryAttempt) {
                dlqMetrics.recordRetry(HotArticleKafkaTopics.originalTopic(record.topic()), deliveryAttempt);
            }

            @Override
            public void recovered(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                  Exception exception) {
                dlqMetrics.recordDlq(HotArticleKafkaTopics.originalTopic(record.topic()));
            }

            @Override
            public void recoveryFailed(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                       Exception original,
                                       Exception failure) {
                dlqMetrics.recordDlqPublishFailure(HotArticleKafkaTopics.originalTopic(record.topic()));
            }
        });
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dlqBatchKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Value("${modu.kafka.failure.replay-batch-size:100}") int replayBatchSize,
            @Value("${modu.kafka.failure.replay-interval-ms:30000}") long replayIntervalMillis,
            @Value("${modu.kafka.failure.replay-send-timeout-seconds:5}") long replaySendTimeoutSeconds
    ) {
        Map<String, Object> dlqConsumerProperties = new HashMap<>(consumerFactory.getConfigurationProperties());
        dlqConsumerProperties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, replayBatchSize);
        dlqConsumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        long worstCaseBatchMillis = replayBatchSize * replaySendTimeoutSeconds * 1_000L;
        long minimumPollIntervalMillis = replayIntervalMillis + worstCaseBatchMillis + 60_000L;
        dlqConsumerProperties.put(
                ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                Math.max(DEFAULT_MAX_POLL_INTERVAL_MILLIS, minimumPollIntervalMillis)
        );

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(dlqConsumerProperties));
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setIdleBetweenPolls(replayIntervalMillis);

        // DLQ 재발행 자체가 실패하면 offset을 넘기지 않고 같은 batch를 계속 보존한다.
        DefaultErrorHandler replayErrorHandler = new DefaultErrorHandler(
                new FixedBackOff(5_000L, Long.MAX_VALUE)
        );
        replayErrorHandler.setAckAfterHandle(false);
        factory.setCommonErrorHandler(replayErrorHandler);
        return factory;
    }
}
