package board.notification.controller;

import board.notification.model.NotificationBundle;
import board.notification.service.NotificationService;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/users/{userId}")
    public List<NotificationResponse> readAll(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return notificationService.readAll(userId, limit).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Getter
    @RequiredArgsConstructor
    public static class NotificationResponse {
        private final String notificationId;
        @JsonSerialize(using = ToStringSerializer.class)
        private final Long articleId;
        private final String title;
        private final long commentCount;
        private final long likeCount;
        private final long eventCount;
        private final LocalDateTime updatedAt;

        public static NotificationResponse from(NotificationBundle bundle) {
            return new NotificationResponse(
                    bundle.getNotificationId(),
                    bundle.getArticleId(),
                    bundle.getTitle(),
                    bundle.getCommentCount(),
                    bundle.getLikeCount(),
                    bundle.getEventCount(),
                    bundle.getUpdatedAt()
            );
        }
    }
}
