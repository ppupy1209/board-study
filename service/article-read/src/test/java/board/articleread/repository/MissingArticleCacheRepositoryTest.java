package board.articleread.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissingArticleCacheRepositoryTest {
    private static final Long ARTICLE_ID = 9223372036854775000L;
    private static final String KEY = "article-read::missing-article::" + ARTICLE_ID;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private MissingArticleCacheRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MissingArticleCacheRepository(redisTemplate);
    }

    @Test
    void readsMissingMarker() {
        when(redisTemplate.hasKey(KEY)).thenReturn(true);

        assertThat(repository.isMissing(ARTICLE_ID)).isTrue();
    }

    @Test
    void storesMissingMarkerWithShortJitteredTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        for (int i = 0; i < 20; i++) {
            repository.markMissing(ARTICLE_ID);
        }

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations, times(20)).set(eq(KEY), eq("MISSING"), ttlCaptor.capture());
        assertThat(ttlCaptor.getAllValues()).allSatisfy(ttl ->
                assertThat(ttl.getSeconds()).isBetween(
                        MissingArticleCacheRepository.BASE_TTL_SECONDS,
                        MissingArticleCacheRepository.BASE_TTL_SECONDS
                                + MissingArticleCacheRepository.TTL_JITTER_SECONDS
                )
        );
    }
}