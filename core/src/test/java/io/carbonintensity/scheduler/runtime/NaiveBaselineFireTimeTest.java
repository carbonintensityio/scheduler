package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.spi.PlanningConstraints;
import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.runtime.impl.annotation.GreenScheduledAnnotationParser;
import io.carbonintensity.scheduler.test.helper.AnnotationUtil;
import io.carbonintensity.scheduler.test.helper.DisabledDummyCarbonIntensityApi;

/**
 * Verifies naiveBaselineFireTime() - the job-specific naive baseline a carbon-impact-enabled job's savings are
 * compared against (CIIO-278) - for both schedule strategies that support {@code carbonImpact}. Never depends on
 * execution history, only on the schedule's own configuration and the actual fire time being queried.
 */
class NaiveBaselineFireTimeTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    private SimpleScheduler scheduler;

    @AfterEach
    void afterEach() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Test
    void fixedWindowBaselineIsTheFallbackCronsOccurrenceOnTheSameDay() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .fixedWindow("9:30 11:45")
                .duration("15m")
                .cron("0 15 10 * * ?")
                .carbonIntensityZone("NL")
                .timeZone("Europe/Amsterdam")
                .build();

        SimpleScheduler.FixedWindowTrigger trigger = (SimpleScheduler.FixedWindowTrigger) newTrigger(greenScheduled,
                Clock.system(ZONE));

        ZonedDateTime actualFireTime = at(LocalDate.of(2026, 9, 4), LocalTime.of(10, 5));

        assertThat(trigger.naiveBaselineFireTime(actualFireTime))
                .isEqualTo(at(LocalDate.of(2026, 9, 4), LocalTime.of(10, 15)));
    }

    @Test
    void fixedWindowBaselineTracksTheActualFireTimesOwnDayNotTheSchedulersCurrentDay() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .fixedWindow("9:30 11:45")
                .duration("15m")
                .cron("0 15 10 * * ?")
                .carbonIntensityZone("NL")
                .timeZone("Europe/Amsterdam")
                .build();

        SimpleScheduler.FixedWindowTrigger trigger = (SimpleScheduler.FixedWindowTrigger) newTrigger(greenScheduled,
                Clock.system(ZONE));

        // an execution from a different day than "now" - as when the batch processes yesterday's history
        ZonedDateTime actualFireTimeYesterday = at(LocalDate.of(2026, 9, 3), LocalTime.of(9, 40));

        assertThat(trigger.naiveBaselineFireTime(actualFireTimeYesterday))
                .isEqualTo(at(LocalDate.of(2026, 9, 3), LocalTime.of(10, 15)));
    }

    @Test
    void successiveBaselineIsTheNearestPlainIntervalOccurrence() {
        ZonedDateTime jobStart = at(LocalDate.of(2026, 9, 1), LocalTime.of(0, 0));
        Clock clock = Clock.fixed(jobStart.toInstant(), ZONE);

        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 4h 4h") // minGap = maxGap = 4h -> average interval is exactly 4h
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();

        SimpleScheduler.SuccessiveTrigger trigger = (SimpleScheduler.SuccessiveTrigger) newTrigger(greenScheduled, clock);

        // occurrences of the virtual, carbon-unaware interval trigger would be at 00:00, 04:00, 08:00, 12:00, ...
        assertThat(trigger.naiveBaselineFireTime(jobStart.plusHours(1))) // closer to 00:00 than to 04:00
                .isEqualTo(jobStart);
        assertThat(trigger.naiveBaselineFireTime(jobStart.plusHours(3))) // closer to 04:00 than to 00:00
                .isEqualTo(jobStart.plusHours(4));
        assertThat(trigger.naiveBaselineFireTime(jobStart.plusHours(9).plusMinutes(30))) // closest to 08:00
                .isEqualTo(jobStart.plusHours(8));
    }

    @Test
    void successiveBaselineDoesNotDependOnPreviousExecutionsHavingBeenRecorded() {
        // the whole point of this design: the batch can process any single execution window in isolation, without
        // needing to know what (if anything) fired before it - see the CarbonImpactHistory cross-batch-boundary
        // problem this replaces.
        ZonedDateTime jobStart = at(LocalDate.of(2026, 9, 1), LocalTime.of(0, 0));
        Clock clock = Clock.fixed(jobStart.toInstant(), ZONE);

        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 4h 4h")
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();

        SimpleScheduler.SuccessiveTrigger firstTrigger = (SimpleScheduler.SuccessiveTrigger) newTrigger(greenScheduled,
                clock);
        SimpleScheduler.SuccessiveTrigger secondTrigger = (SimpleScheduler.SuccessiveTrigger) newTrigger(greenScheduled,
                clock);

        // two independently constructed triggers for the same schedule, neither of which has ever fired, agree on
        // the baseline for the same actual fire time.
        ZonedDateTime someFireTimeMuchLater = jobStart.plusDays(30).plusHours(9);
        assertThat(firstTrigger.naiveBaselineFireTime(someFireTimeMuchLater))
                .isEqualTo(secondTrigger.naiveBaselineFireTime(someFireTimeMuchLater));
    }

    private Trigger newTrigger(GreenScheduled greenScheduled, Clock clock) {
        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(new DisabledDummyCarbonIntensityApi());
        config.setClock(clock);
        scheduler = new SimpleScheduler(config);

        PlanningConstraints constraints = GreenScheduledAnnotationParser.createConstraints("test", greenScheduled, clock);
        return scheduler.createTrigger("test", "Test#test",
                GreenScheduledAnnotationParser.parseOverdueGracePeriod(greenScheduled, Duration.ofSeconds(30)),
                constraints);
    }

    private ZonedDateTime at(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(LocalDateTime.of(date, time), ZONE);
    }
}
