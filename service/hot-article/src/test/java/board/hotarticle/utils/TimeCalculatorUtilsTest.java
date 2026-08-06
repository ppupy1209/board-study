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
    void expiresTodayArticleAtNextDayZeroSix() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 15, 30);

        Duration duration = TimeCalculatorUtils.calculateDurationToExpiration(
                LocalDate.of(2026, 8, 6), now
        );

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
    void keepsYesterdayAndTodayActiveUntilZeroFive() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 0, 5, 59);

        assertThat(TimeCalculatorUtils.isActiveHotArticleDate(
                LocalDateTime.of(2026, 8, 5, 12, 0), now
        )).isTrue();
        assertThat(TimeCalculatorUtils.isActiveHotArticleDate(
                LocalDateTime.of(2026, 8, 6, 0, 1), now
        )).isTrue();
        assertThat(TimeCalculatorUtils.isActiveHotArticleDate(
                LocalDateTime.of(2026, 8, 4, 12, 0), now
        )).isFalse();
    }

    @Test
    void expiresYesterdayArticleAtZeroSix() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 0, 4);

        assertThat(TimeCalculatorUtils.calculateDurationToExpiration(
                LocalDate.of(2026, 8, 5), now
        )).isEqualTo(Duration.ofMinutes(2));
        assertThat(TimeCalculatorUtils.isActiveHotArticleDate(
                LocalDateTime.of(2026, 8, 5, 12, 0), LocalDateTime.of(2026, 8, 6, 0, 6)
        )).isFalse();
    }
}
