package board.hotarticle.service;

import board.common.event.payload.EventType;
import board.hotarticle.kafka.HotArticleEventPosition;
import board.hotarticle.kafka.HotArticleEventProcessingInProgressException;
import board.hotarticle.repository.HotArticleEventVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotArticleEventVersionGuardTest {
    private static final Long ARTICLE_ID = 1L;
    private static final HotArticleEventPosition POSITION = new HotArticleEventPosition(
            EventType.Topic.BOARD_VIEW,
            0,
            10L
    );

    @Mock
    private HotArticleEventVersionRepository versionRepository;
    @Mock
    private Runnable processing;

    private HotArticleEventVersionGuard versionGuard;

    @BeforeEach
    void setUp() {
        versionGuard = new HotArticleEventVersionGuard(versionRepository);
    }

    @Test
    void processesNewerEventAndSavesItsOffset() {
        when(versionRepository.tryLock(eq(POSITION), eq(ARTICLE_ID), anyString())).thenReturn(true);
        when(versionRepository.readLatestOffset(POSITION, ARTICLE_ID)).thenReturn(9L);

        boolean handled = versionGuard.runIfLatest(POSITION, ARTICLE_ID, processing);

        assertThat(handled).isTrue();
        verify(processing).run();
        verify(versionRepository).saveLatestOffset(POSITION, ARTICLE_ID);
        verify(versionRepository).unlock(eq(POSITION), eq(ARTICLE_ID), anyString());
    }

    @Test
    void skipsEventWhenSameOrNewerOffsetWasAlreadyHandled() {
        when(versionRepository.tryLock(eq(POSITION), eq(ARTICLE_ID), anyString())).thenReturn(true);
        when(versionRepository.readLatestOffset(POSITION, ARTICLE_ID)).thenReturn(10L);

        boolean handled = versionGuard.runIfLatest(POSITION, ARTICLE_ID, processing);

        assertThat(handled).isFalse();
        verify(processing, never()).run();
        verify(versionRepository, never()).saveLatestOffset(POSITION, ARTICLE_ID);
        verify(versionRepository).unlock(eq(POSITION), eq(ARTICLE_ID), anyString());
    }

    @Test
    void doesNotSaveOffsetWhenProcessingFails() {
        when(versionRepository.tryLock(eq(POSITION), eq(ARTICLE_ID), anyString())).thenReturn(true);
        when(versionRepository.readLatestOffset(POSITION, ARTICLE_ID)).thenReturn(null);
        RuntimeException failure = new RuntimeException("processing failed");
        org.mockito.Mockito.doThrow(failure).when(processing).run();

        assertThatThrownBy(() -> versionGuard.runIfLatest(POSITION, ARTICLE_ID, processing))
                .isSameAs(failure);

        verify(versionRepository, never()).saveLatestOffset(POSITION, ARTICLE_ID);
        verify(versionRepository).unlock(eq(POSITION), eq(ARTICLE_ID), anyString());
    }

    @Test
    void reportsLockContentionAsRetryableFailure() {
        when(versionRepository.tryLock(eq(POSITION), eq(ARTICLE_ID), anyString())).thenReturn(false);

        assertThatThrownBy(() -> versionGuard.runIfLatest(POSITION, ARTICLE_ID, processing))
                .isInstanceOf(HotArticleEventProcessingInProgressException.class);

        verify(processing, never()).run();
        verify(versionRepository, never()).readLatestOffset(POSITION, ARTICLE_ID);
    }
}
