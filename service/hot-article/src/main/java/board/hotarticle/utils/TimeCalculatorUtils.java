package board.hotarticle.utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeCalculatorUtils {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime TODAY_RANKING_START = LocalTime.of(0, 6);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static Duration calculateDurationToMidnight() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        return calculateDurationToMidnight(now);
    }

    public static Duration calculateDurationToExpiration(LocalDateTime createdAt) {
        return calculateDurationToExpiration(createdAt.toLocalDate(), LocalDateTime.now(BUSINESS_ZONE));
    }

    public static Duration calculateDurationToExpiration(String dateStr) {
        return calculateDurationToExpiration(
                LocalDate.parse(dateStr, DATE_FORMATTER),
                LocalDateTime.now(BUSINESS_ZONE)
        );
    }

    static Duration calculateDurationToExpiration(LocalDate articleDate, LocalDateTime now) {
        LocalDateTime expiresAt = articleDate.plusDays(1).atTime(TODAY_RANKING_START);
        return Duration.between(now, expiresAt);
    }

    static Duration calculateDurationToMidnight(LocalDateTime now) {
        LocalDateTime midnight = now.plusDays(1).with(LocalTime.MIDNIGHT);
        return Duration.between(now, midnight);
    }

    public static String calculateHotArticleDate() {
        return calculateHotArticleDate(LocalDateTime.now(BUSINESS_ZONE));
    }

    static String calculateHotArticleDate(LocalDateTime now) {
        LocalDate hotArticleDate = now.toLocalTime().isBefore(TODAY_RANKING_START)
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
        return DATE_FORMATTER.format(hotArticleDate);
    }

    public static boolean isActiveHotArticleDate(LocalDateTime createdAt) {
        return isActiveHotArticleDate(createdAt, LocalDateTime.now(BUSINESS_ZONE));
    }

    static boolean isActiveHotArticleDate(LocalDateTime createdAt, LocalDateTime now) {
        if (createdAt == null) {
            return false;
        }
        LocalDate createdDate = createdAt.toLocalDate();
        return createdDate.equals(now.toLocalDate())
                || (now.toLocalTime().isBefore(TODAY_RANKING_START)
                && createdDate.equals(now.toLocalDate().minusDays(1)));
    }
}
