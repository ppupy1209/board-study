package board.hotarticle.service;

import board.common.event.Event;
import board.common.event.payload.EventType;
import board.hotarticle.client.ArticleClient;
import board.hotarticle.repository.HotArticleListRepository;
import board.hotarticle.repository.HotArticleQueryModel;
import board.hotarticle.repository.HotArticleQueryModelRepository;
import board.hotarticle.service.response.HotArticleResponse;
import board.hotarticle.service.eventhandler.EventHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class HotArticleServiceTest {
    @InjectMocks
    HotArticleService hotArticleService;
    @Mock
    List<EventHandler> eventHandlers;
    @Mock
    HotArticleScoreUpdater hotArticleScoreUpdater;
    @Mock
    ArticleClient articleClient;
    @Mock
    HotArticleListRepository hotArticleListRepository;
    @Mock
    HotArticleQueryModelRepository hotArticleQueryModelRepository;
    @Mock
    HotArticleReadModelMetrics hotArticleReadModelMetrics;

    @Test
    void handleEventIfEventHandlerNotFoundTest() {
        Event event = mock(Event.class);
        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(false);
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler));

        hotArticleService.handleEvent(event);


        verify(eventHandler, never()).handle(event);
        verify(hotArticleScoreUpdater, never()).update(event, eventHandler);
    }

    @Test
    void handleEventIfArticleCreatedEventTest() {
        Event event = mock(Event.class);
        given(event.getType()).willReturn(EventType.ARTICLE_CREATED);

        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(true);
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler));

        hotArticleService.handleEvent(event);

        verify(eventHandler).handle(event);
        verify(hotArticleScoreUpdater, never()).update(event, eventHandler);
    }

    @Test
    void handleEventIfArticleDeletedEventTest() {
        Event event = mock(Event.class);
        given(event.getType()).willReturn(EventType.ARTICLE_DELETED);

        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(true);
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler));

        hotArticleService.handleEvent(event);

        verify(eventHandler).handle(event);
        verify(hotArticleScoreUpdater, never()).update(event, eventHandler);
    }

    @Test
    void handleEventIfArticleUpdatedEventTest() {
        Event event = mock(Event.class);
        given(event.getType()).willReturn(EventType.ARTICLE_UPDATED);

        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(true);
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler));

        hotArticleService.handleEvent(event);

        verify(eventHandler).handle(event);
        verify(hotArticleScoreUpdater, never()).update(event, eventHandler);
    }

    @Test
    void handleEventIfScoreUpdatableEventTest() {
        Event event = mock(Event.class);
        given(event.getType()).willReturn(mock(EventType.class));

        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(true);
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler));

        hotArticleService.handleEvent(event);

        verify(eventHandler, never()).handle(event);
        verify(hotArticleScoreUpdater).update(event, eventHandler);
    }

    @Test
    void readAllUsesQueryModelsWithoutArticleServiceCalls() {
        String date = today();
        HotArticleQueryModel first = mock(HotArticleQueryModel.class);
        HotArticleQueryModel second = mock(HotArticleQueryModel.class);
        given(first.getArticleId()).willReturn(1L);
        given(second.getArticleId()).willReturn(2L);
        given(hotArticleListRepository.readAll(date)).willReturn(List.of(1L, 2L));
        given(hotArticleQueryModelRepository.readAll(List.of(1L, 2L)))
                .willReturn(Map.of(1L, first, 2L, second));

        List<HotArticleResponse> responses = hotArticleService.readAll(date);

        assertThat(responses).extracting(HotArticleResponse::getArticleId).containsExactly(1L, 2L);
        verify(hotArticleReadModelMetrics).hit(2);
        verify(articleClient, never()).read(anyLong());
    }

    @Test
    void readAllReturnsEmptyForPastDate() {
        List<HotArticleResponse> responses = hotArticleService.readAll(date(-1));

        assertThat(responses).isEmpty();
        verifyNoInteractions(hotArticleListRepository, hotArticleQueryModelRepository, articleClient);
    }

    @Test
    void readAllFillsMissingQueryModelFromArticleServiceOnce() {
        String date = today();
        ArticleClient.ArticleResponse article = mock(ArticleClient.ArticleResponse.class);
        given(article.getArticleId()).willReturn(1L);
        given(article.getTitle()).willReturn("title");
        given(article.getCreatedAt()).willReturn(LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay());
        given(hotArticleListRepository.readAll(date)).willReturn(List.of(1L));
        given(hotArticleQueryModelRepository.readAll(List.of(1L))).willReturn(Map.of());
        given(articleClient.read(1L)).willReturn(article);

        List<HotArticleResponse> responses = hotArticleService.readAll(date);

        assertThat(responses).extracting(HotArticleResponse::getArticleId).containsExactly(1L);
        verify(hotArticleReadModelMetrics).miss(1);
        verify(hotArticleReadModelMetrics).originCall(1);
        verify(hotArticleQueryModelRepository).createOrUpdate(
                any(HotArticleQueryModel.class), any(Duration.class)
        );
    }

    private String today() {
        return date(0);
    }

    private String date(int daysToAdd) {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(daysToAdd)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
