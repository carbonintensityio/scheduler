package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class CarbonImpactBatchTriggerTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalTime WINDOW_START = LocalTime.of(2, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(6, 0);

    @Test
    void shouldNotFireBeforeTheJitteredTarget() {
        CarbonImpactBatchTrigger trigger = newTrigger(Duration.ofMinutes(30));
        ZonedDateTime justBefore = at(LocalDate.of(2026, 9, 4), LocalTime.of(2, 29, 59));

        assertThat(trigger.evaluate(justBefore)).isNull();
    }

    @Test
    void shouldFireAtTheJitteredTarget() {
        CarbonImpactBatchTrigger trigger = newTrigger(Duration.ofMinutes(30));
        ZonedDateTime target = at(LocalDate.of(2026, 9, 4), LocalTime.of(2, 30));

        assertThat(trigger.evaluate(target)).isEqualTo(target);
        assertThat(trigger.getPreviousFireTime()).isEqualTo(target.toInstant());
    }

    @Test
    void shouldNotFireTwiceOnTheSameDay() {
        CarbonImpactBatchTrigger trigger = newTrigger(Duration.ofMinutes(30));
        ZonedDateTime target = at(LocalDate.of(2026, 9, 4), LocalTime.of(2, 30));
        trigger.evaluate(target);

        ZonedDateTime laterSameDay = at(LocalDate.of(2026, 9, 4), LocalTime.of(5, 0));

        assertThat(trigger.evaluate(laterSameDay)).isNull();
    }

    @Test
    void shouldFireAgainOnTheNextDay() {
        CarbonImpactBatchTrigger trigger = newTrigger(Duration.ofMinutes(30));
        trigger.evaluate(at(LocalDate.of(2026, 9, 4), LocalTime.of(2, 30)));

        ZonedDateTime nextDayTarget = at(LocalDate.of(2026, 9, 5), LocalTime.of(2, 30));

        assertThat(trigger.evaluate(nextDayTarget)).isEqualTo(nextDayTarget);
    }

    @Test
    void getNextFireTimeShouldReportTodayBeforeFiring() {
        CarbonImpactBatchTrigger trigger = newTrigger(Duration.ofMinutes(30));
        Clock clock = Clock.fixed(at(LocalDate.of(2026, 9, 4), LocalTime.of(1, 0)).toInstant(), ZONE);
        CarbonImpactBatchTrigger triggerWithClock = new CarbonImpactBatchTrigger(clock, WINDOW_START, WINDOW_END,
                Duration.ofMinutes(30));

        assertThat(triggerWithClock.getNextFireTime())
                .isEqualTo(at(LocalDate.of(2026, 9, 4), LocalTime.of(2, 30)).toInstant());
    }

    @Test
    void getNextFireTimeShouldReportTomorrowAfterFiringToday() {
        Clock clock = Clock.fixed(at(LocalDate.of(2026, 9, 4), LocalTime.of(2, 30)).toInstant(), ZONE);
        CarbonImpactBatchTrigger trigger = new CarbonImpactBatchTrigger(clock, WINDOW_START, WINDOW_END,
                Duration.ofMinutes(30));
        trigger.evaluate(ZonedDateTime.now(clock));

        assertThat(trigger.getNextFireTime())
                .isEqualTo(at(LocalDate.of(2026, 9, 5), LocalTime.of(2, 30)).toInstant());
    }

    @Test
    void shouldRejectAWindowThatDoesNotStartBeforeItEnds() {
        assertThatThrownBy(() -> new CarbonImpactBatchTrigger(Clock.systemUTC(), LocalTime.of(6, 0), LocalTime.of(2, 0),
                Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectJitterOutsideTheWindow() {
        assertThatThrownBy(() -> new CarbonImpactBatchTrigger(Clock.systemUTC(), WINDOW_START, WINDOW_END,
                Duration.ofHours(4)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CarbonImpactBatchTrigger(Clock.systemUTC(), WINDOW_START, WINDOW_END,
                Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void randomJitterShouldStayWithinTheWindow() {
        Duration windowLength = Duration.between(WINDOW_START, WINDOW_END);

        for (int i = 0; i < 100; i++) {
            Duration jitter = CarbonImpactBatchTrigger.randomJitter(WINDOW_START, WINDOW_END);
            assertThat(jitter).isGreaterThanOrEqualTo(Duration.ZERO).isLessThan(windowLength);
        }
    }

    private CarbonImpactBatchTrigger newTrigger(Duration jitter) {
        return new CarbonImpactBatchTrigger(Clock.systemUTC(), WINDOW_START, WINDOW_END, jitter);
    }

    private ZonedDateTime at(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(LocalDateTime.of(date, time), ZONE);
    }
}
