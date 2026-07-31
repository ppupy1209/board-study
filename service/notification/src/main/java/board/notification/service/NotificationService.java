package board.notification.service;

import board.common.event.Event;
import board.common.event.EventPayload;
import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.ArticleDeletedEventPayload;
import board.common.event.payload.ArticleLikedEventPayload;
import board.common.event.payload.ArticleUpdatedEventPayload;
import board.common.event.payload.CommentCreatedEventPayload;
import board.notification.client.ArticleClient;
import board.notification.model.ArticleNotificationTarget;
import board.notification.model.NotificationBundle;
import board.notification.repository.ArticleNotificationTargetRepository;
import board.notification.repository.NotificationBundleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final ArticleNotificationTargetRepository targetRepository;
    private final NotificationBundleRepository bundleRepository;
    private final ArticleClient articleClient;
    private final NotificationMetrics metrics;

    public void handleEvent(Event<EventPayload> event) {
        switch (event.getType()) {
            case ARTICLE_CREATED -> saveTarget((ArticleCreatedEventPayload) event.getPayload());
            case ARTICLE_UPDATED -> updateTarget((ArticleUpdatedEventPayload) event.getPayload());
            case ARTICLE_DELETED -> targetRepository.delete(((ArticleDeletedEventPayload) event.getPayload()).getArticleId());
            case COMMENT_CREATED -> addComment(event.getEventId(), (CommentCreatedEventPayload) event.getPayload());
            case ARTICLE_LIKED -> addLike(event.getEventId(), (ArticleLikedEventPayload) event.getPayload());
            default -> metrics.record("ignored");
        }
    }

    public List<NotificationBundle> readAll(Long userId, int limit) {
        return bundleRepository.readAll(userId, Math.min(Math.max(limit, 1), 50));
    }

    private void saveTarget(ArticleCreatedEventPayload payload) {
        targetRepository.save(new ArticleNotificationTarget(payload.getArticleId(), payload.getWriterId(), payload.getTitle()));
    }

    private void updateTarget(ArticleUpdatedEventPayload payload) {
        targetRepository.save(new ArticleNotificationTarget(payload.getArticleId(), payload.getWriterId(), payload.getTitle()));
    }

    private void addComment(Long eventId, CommentCreatedEventPayload payload) {
        addReaction(eventId, payload.getArticleId(), payload.getWriterId(), payload.getCreatedAt(), "commentCount");
    }

    private void addLike(Long eventId, ArticleLikedEventPayload payload) {
        addReaction(eventId, payload.getArticleId(), payload.getUserId(), payload.getCreatedAt(), "likeCount");
    }

    private void addReaction(
            Long eventId,
            Long articleId,
            Long actorId,
            LocalDateTime occurredAt,
            String countField
    ) {
        Optional<ArticleNotificationTarget> target = findTarget(articleId);
        if (target.isEmpty()) {
            metrics.record("target_missing");
            return;
        }
        if (Objects.equals(target.get().getWriterId(), actorId)) {
            metrics.record("self_ignored");
            return;
        }

        boolean added = bundleRepository.add(
                eventId,
                target.get().getWriterId(),
                articleId,
                target.get().getTitle(),
                countField,
                occurredAt == null ? LocalDateTime.now() : occurredAt
        );
        metrics.record(added ? "bundled" : "duplicate");
    }

    private Optional<ArticleNotificationTarget> findTarget(Long articleId) {
        Optional<ArticleNotificationTarget> cached = targetRepository.read(articleId);
        if (cached.isPresent()) {
            return cached;
        }
        ArticleClient.ArticleResponse article = articleClient.read(articleId);
        if (article == null) {
            return Optional.empty();
        }
        ArticleNotificationTarget target = ArticleNotificationTarget.from(article);
        targetRepository.save(target);
        return Optional.of(target);
    }
}
