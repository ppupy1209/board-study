package board.hotarticle.service.eventhandler;

import board.common.event.Event;
import board.common.event.payload.ArticleUpdatedEventPayload;
import board.hotarticle.repository.HotArticleQueryModel;
import board.hotarticle.repository.HotArticleQueryModelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ArticleUpdatedEventHandlerTest {
    @InjectMocks
    ArticleUpdatedEventHandler handler;
    @Mock
    HotArticleQueryModelRepository hotArticleQueryModelRepository;

    @Test
    void updatesTodayArticleUntilMidnightWithGrace() {
        Event<ArticleUpdatedEventPayload> event = mock(Event.class);
        ArticleUpdatedEventPayload payload = mock(ArticleUpdatedEventPayload.class);
        given(event.getPayload()).willReturn(payload);
        given(payload.getCreatedAt()).willReturn(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        handler.handle(event);

        verify(hotArticleQueryModelRepository).createOrUpdate(
                any(HotArticleQueryModel.class), any(Duration.class)
        );
    }

    @Test
    void ignoresArticleCreatedBeforeToday() {
        Event<ArticleUpdatedEventPayload> event = mock(Event.class);
        ArticleUpdatedEventPayload payload = mock(ArticleUpdatedEventPayload.class);
        given(event.getPayload()).willReturn(payload);
        given(payload.getCreatedAt()).willReturn(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1));

        handler.handle(event);

        verifyNoInteractions(hotArticleQueryModelRepository);
    }
}
