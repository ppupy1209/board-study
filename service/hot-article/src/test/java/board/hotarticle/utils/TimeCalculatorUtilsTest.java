package board.hotarticle.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeCalculatorUtilsTest {
    @Test
    void calculatesDurationUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 15, 30);

        Duration duration = TimeCalculatorUtils.calculateDurationToMidnight(now);

        assertThat(duration).isEqualTo(Duration.ofHours(8).plusMinutes(30));
    }

    @Test
    void expiresAfterTheLastPreviousDayDisplayMinute() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 15, 30);

        Duration duration = TimeCalculatorUtils.calculateDurationToMidnightWithGrace(now);

        assertThat(duration).isEqualTo(Duration.ofHours(8).plusMinutes(36));
    }

    @Test
    void usesYesterdayUntilZeroFiveAndTodayFromZeroSix() {
        assertThat(TimeCalculatorUtils.calculateHotArticleDate(
                LocalDateTime.of(2026, 8, 6, 0, 5, 59)
        )).isEqualTo("20260805");
        assertThat(TimeCalculatorUtils.calculateHotArticleDate(
                LocalDateTime.of(2026, 8, 6, 0, 6)
        )).isEqualTo("20260806");
    }

    @Test
    void identifiesTodayByCreationTime() {
        LocalDate today = LocalDate.of(2026, 8, 6);

        assertThat(TimeCalculatorUtils.isToday(today.atTime(23, 59), today)).isTrue();
        assertThat(TimeCalculatorUtils.isToday(today.minusDays(1).atTime(23, 59), today)).isFalse();
        assertThat(TimeCalculatorUtils.isToday((LocalDateTime) null, today)).isFalse();
    }

    @Test
    void identifiesTodayByDateString() {
        LocalDate today = LocalDate.of(2026, 8, 6);

        assertThat(TimeCalculatorUtils.isToday("20260806", today)).isTrue();
        assertThat(TimeCalculatorUtils.isToday("20260805", today)).isFalse();
    }
}
