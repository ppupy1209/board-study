package board.hotarticle.kafka;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.Map;

public class HotArticleKafkaFailureClassifier {
    private static final Map<Class<? extends Throwable>, Boolean> CLASSIFICATIONS = Map.of(
            RedisConnectionFailureException.class, true,
            QueryTimeoutException.class, true,
            TransientDataAccessException.class, true,
            HotArticleEventProcessingInProgressException.class, true
    );

    public void applyTo(DefaultErrorHandler errorHandler) {
        errorHandler.setClassifications(CLASSIFICATIONS, false);
    }

    public boolean isRetryable(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            for (Class<? extends Throwable> retryableException : CLASSIFICATIONS.keySet()) {
                if (retryableException.isInstance(cause)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
