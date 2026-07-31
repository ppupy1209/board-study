package board.notification.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Getter
@AllArgsConstructor
public class NotificationBundle {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private String notificationId;
    private Long articleId;
    private String title;
    private long commentCount;
    private long likeCount;
    private LocalDateTime updatedAt;

    public long getEventCount() {
        return commentCount + likeCount;
    }

    public static NotificationBundle from(String notificationId, Map<Object, Object> values) {
        return new NotificationBundle(
                notificationId,
                Long.valueOf(String.valueOf(values.get("articleId"))),
                String.valueOf(values.get("title")),
                longValue(values.get("commentCount")),
                longValue(values.get("likeCount")),
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(longValue(values.get("updatedAt"))),
                        SERVICE_ZONE
                )
        );
    }

    private static long longValue(Object value) {
        return value == null ? 0 : Long.parseLong(String.valueOf(value));
    }
}
