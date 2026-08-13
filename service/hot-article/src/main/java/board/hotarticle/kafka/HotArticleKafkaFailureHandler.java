package board.hotarticle.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

public final class HotArticleKafkaFailureHandler {
    private static final Duration DLQ_SEND_TIMEOUT = Duration.ofSeconds(5);

    private HotArticleKafkaFailureHandler() {
    }

    public static DefaultErrorHandler create(
            KafkaTemplate<String, String> kafkaTemplate,
            HotArticleDlqMetrics dlqMetrics,
            long retryIntervalMillis,
            long maxAttempts
    ) {
        DeadLetterPublishingRecoverer recoverer = createRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryIntervalMillis, Math.max(0, maxAttempts - 1))
        );
        errorHandler.setCommitRecovered(true);
        errorHandler.setRetryListeners(createRetryListener(dlqMetrics));
        return errorHandler;
    }

    private static DeadLetterPublishingRecoverer createRecoverer(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        HotArticleKafkaTopics.dlqTopic(record.topic()),
                        -1
                )
        );
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(DLQ_SEND_TIMEOUT);
        return recoverer;
    }

    private static RetryListener createRetryListener(HotArticleDlqMetrics dlqMetrics) {
        return new RetryListener() {
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
        };
    }
}
