package board.hotarticle.service.eventhandler;

import board.common.event.Event;
import board.common.event.payload.ArticleCreatedEventPayload;
import board.hotarticle.repository.ArticleCreatedTimeRepository;
import board.hotarticle.repository.HotArticleQueryModel;
import board.hotarticle.repository.HotArticleQueryModelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ArticleCreatedEventHandlerTest {
    @InjectMocks
    ArticleCreatedEventHandler handler;
    @Mock
    ArticleCreatedTimeRepository articleCreatedTimeRepository;
    @Mock
    HotArticleQueryModelRepository hotArticleQueryModelRepository;

    @Test
    void storesTodayArticleUntilMidnightWithGrace() {
        Event<ArticleCreatedEventPayload> event = mock(Event.class);
        ArticleCreatedEventPayload payload = mock(ArticleCreatedEventPayload.class);
        LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        given(event.getPayload()).willReturn(payload);
        given(payload.getArticleId()).willReturn(1L);
        given(payload.getCreatedAt()).willReturn(createdAt);

        handler.handle(event);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(articleCreatedTimeRepository).createOrUpdate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(createdAt),
                ttlCaptor.capture()
        );
        verify(hotArticleQueryModelRepository).createOrUpdate(
                any(HotArticleQueryModel.class), org.mockito.ArgumentMatchers.eq(ttlCaptor.getValue())
        );
        assertThat(ttlCaptor.getValue()).isPositive();
        assertThat(ttlCaptor.getValue()).isLessThanOrEqualTo(Duration.ofHours(25));
    }

    @Test
    void ignoresArticleCreatedBeforeToday() {
        Event<ArticleCreatedEventPayload> event = mock(Event.class);
        ArticleCreatedEventPayload payload = mock(ArticleCreatedEventPayload.class);
        given(event.getPayload()).willReturn(payload);
        given(payload.getCreatedAt()).willReturn(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1));

        handler.handle(event);

        verifyNoInteractions(articleCreatedTimeRepository, hotArticleQueryModelRepository);
    }
}
