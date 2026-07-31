package board.notification.service;

import board.common.event.Event;
import board.common.event.payload.ArticleLikedEventPayload;
import board.common.event.payload.CommentCreatedEventPayload;
import board.common.event.payload.EventType;
import board.notification.client.ArticleClient;
import board.notification.model.ArticleNotificationTarget;
import board.notification.repository.ArticleNotificationTargetRepository;
import board.notification.repository.NotificationBundleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @InjectMocks
    NotificationService notificationService;

    @Mock
    ArticleNotificationTargetRepository targetRepository;
    @Mock
    NotificationBundleRepository bundleRepository;
    @Mock
    ArticleClient articleClient;
    @Mock
    NotificationMetrics metrics;

    @Test
    void ignoresReactionFromArticleWriter() {
        given(targetRepository.read(100L))
                .willReturn(Optional.of(new ArticleNotificationTarget(100L, 1L, "Popular article")));
        Event event = Event.of(
                10L,
                EventType.ARTICLE_LIKED,
                ArticleLikedEventPayload.builder()
                        .articleId(100L)
                        .userId(1L)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        notificationService.handleEvent(event);

        verify(bundleRepository, never()).add(any(), any(), any(), any(), any(), any());
        verify(metrics).record("self_ignored");
    }

    @Test
    void addsCommentToArticleWritersBundle() {
        given(targetRepository.read(100L))
                .willReturn(Optional.of(new ArticleNotificationTarget(100L, 1L, "Popular article")));
        given(bundleRepository.add(any(), any(), any(), any(), any(), any())).willReturn(true);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 31, 12, 0);
        Event event = Event.of(
                11L,
                EventType.COMMENT_CREATED,
                CommentCreatedEventPayload.builder()
                        .articleId(100L)
                        .writerId(2L)
                        .createdAt(occurredAt)
                        .build()
        );

        notificationService.handleEvent(event);

        verify(bundleRepository).add(11L, 1L, 100L, "Popular article", "commentCount", occurredAt);
        verify(metrics).record("bundled");
    }
}
