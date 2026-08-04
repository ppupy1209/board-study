package board.articleread.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleLookupLockRepositoryTest {
    private static final Long ARTICLE_ID = 9223372036854775000L;
    private static final String KEY = "article-read::article-lookup-lock::" + ARTICLE_ID;
    private static final String OWNER = "request-owner";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ArticleLookupLockRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ArticleLookupLockRepository(redisTemplate);
    }

    @Test
    void acquiresLockWithShortTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(KEY, OWNER, ArticleLookupLockRepository.LOCK_TTL))
                .thenReturn(true);

        assertThat(repository.tryAcquire(ARTICLE_ID, OWNER)).isTrue();
        verify(valueOperations).setIfAbsent(
                KEY,
                OWNER,
                ArticleLookupLockRepository.LOCK_TTL
        );
    }
}
