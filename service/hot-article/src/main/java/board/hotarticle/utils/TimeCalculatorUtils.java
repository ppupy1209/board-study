package board.hotarticle.utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeCalculatorUtils {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration EXPIRATION_GRACE = Duration.ofHours(1);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static Duration calculateDurationToMidnight() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        return calculateDurationToMidnight(now);
    }

    public static Duration calculateDurationToMidnightWithGrace() {
        return calculateDurationToMidnightWithGrace(LocalDateTime.now(BUSINESS_ZONE));
    }

    static Duration calculateDurationToMidnightWithGrace(LocalDateTime now) {
        return calculateDurationToMidnight(now).plus(EXPIRATION_GRACE);
    }

    static Duration calculateDurationToMidnight(LocalDateTime now) {
        LocalDateTime midnight = now.plusDays(1).with(LocalTime.MIDNIGHT);
        return Duration.between(now, midnight);
    }

    public static boolean isToday(LocalDateTime dateTime) {
        return isToday(dateTime, LocalDate.now(BUSINESS_ZONE));
    }

    static boolean isToday(LocalDateTime dateTime, LocalDate today) {
        return dateTime != null && dateTime.toLocalDate().equals(today);
    }

    public static boolean isToday(String dateStr) {
        return isToday(dateStr, LocalDate.now(BUSINESS_ZONE));
    }

    static boolean isToday(String dateStr, LocalDate today) {
        return DATE_FORMATTER.format(today).equals(dateStr);
    }
}
