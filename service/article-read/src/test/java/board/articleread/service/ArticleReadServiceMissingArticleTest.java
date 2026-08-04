package board.articleread.service;

import board.articleread.client.ArticleClient;
import board.articleread.client.CommentClient;
import board.articleread.client.LikeClient;
import board.articleread.client.ViewClient;
import board.articleread.repository.ArticleIdListRepository;
import board.articleread.repository.ArticleLookupLockRepository;
import board.articleread.repository.ArticleQueryModelRepository;
import board.articleread.repository.BoardArticleCountRepository;
import board.articleread.repository.MissingArticleCacheRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleReadServiceMissingArticleTest {
    private static final Long ARTICLE_ID = 9223372036854775000L;

    @Mock private ArticleClient articleClient;
    @Mock private CommentClient commentClient;
    @Mock private LikeClient likeClient;
    @Mock private ViewClient viewClient;
    @Mock private ArticleQueryModelRepository articleQueryModelRepository;
    @Mock private ArticleIdListRepository articleIdListRepository;
    @Mock private BoardArticleCountRepository boardArticleCountRepository;
    @Mock private QueryModelMetrics queryModelMetrics;
    @Mock private MissingArticleCacheRepository missingArticleCacheRepository;
    @Mock private ArticleLookupLockRepository articleLookupLockRepository;
    @Mock private ArticleMissingCacheMetrics articleMissingCacheMetrics;

    private ExecutorService executor;
    private ArticleReadService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        service = new ArticleReadService(
                articleClient,
                commentClient,
                likeClient,
                viewClient,
                articleQueryModelRepository,
                articleIdListRepository,
                boardArticleCountRepository,
                queryModelMetrics,
                missingArticleCacheRepository,
                articleLookupLockRepository,
                articleMissingCacheMetrics,
                executor,
                List.of()
        );
        when(articleQueryModelRepository.read(ARTICLE_ID)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void returnsNotFoundWithoutOriginCallWhenMissingMarkerExists() {
        when(missingArticleCacheRepository.isMissing(ARTICLE_ID)).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.read(ARTICLE_ID));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode().value()).isEqualTo(404);
        verify(articleMissingCacheMetrics).hit();
        verifyNoInteractions(articleClient, commentClient, likeClient);
    }

    @Test
    void storesMissingMarkerAfterOriginConfirmsNotFound() {
        when(missingArticleCacheRepository.isMissing(ARTICLE_ID)).thenReturn(false);
        when(articleLookupLockRepository.tryAcquire(eq(ARTICLE_ID), anyString())).thenReturn(true);
        when(articleClient.read(ARTICLE_ID)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.read(ARTICLE_ID));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode().value()).isEqualTo(404);
        verify(missingArticleCacheRepository).markMissing(ARTICLE_ID);
        verify(articleMissingCacheMetrics).stored();
        verify(articleLookupLockRepository).release(eq(ARTICLE_ID), anyString());
        verifyNoInteractions(commentClient, likeClient);
        verify(viewClient, never()).count(ARTICLE_ID);
    }

    @Test
    void waitsForTheFirstRequestInsteadOfCallingOriginAgain() {
        when(missingArticleCacheRepository.isMissing(ARTICLE_ID)).thenReturn(false, true);
        when(articleLookupLockRepository.tryAcquire(eq(ARTICLE_ID), anyString())).thenReturn(false);

        Throwable thrown = catchThrowable(() -> service.read(ARTICLE_ID));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode().value()).isEqualTo(404);
        verify(articleMissingCacheMetrics).coalesced();
        verify(articleMissingCacheMetrics).hit();
        verifyNoInteractions(articleClient, commentClient, likeClient);
        verify(viewClient, never()).count(ARTICLE_ID);
    }
}
