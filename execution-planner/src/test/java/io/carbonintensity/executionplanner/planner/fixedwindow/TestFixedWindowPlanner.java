package io.carbonintensity.executionplanner.planner.fixedwindow;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensityDataFetcher;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityJsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestFixedWindowPlanner {

    FixedWindowPlanner defaultCarbonIntensityScheduler;
    CarbonIntensityDataFetcher carbonIntensityDataFetcher;

    @BeforeEach
    public void setup() {
        carbonIntensityDataFetcher = mock(CarbonIntensityDataFetcher.class);
        defaultCarbonIntensityScheduler = new FixedWindowPlanner(carbonIntensityDataFetcher);
    }

    @Test
    void shouldStandardSchedule() {
        final var parser = new CarbonIntensityJsonParser();
        final var carbonIntensity = parser.parse(ClassLoader.getSystemResourceAsStream("day-ahead-20240824-Z.json"));

        when(carbonIntensityDataFetcher.fetchCarbonIntensity(any()))
                .thenReturn(carbonIntensity);

        ZonedDateTime now = ZonedDateTime.now();

        String cronExpression = "0 0 * * *";
        CronParser cronparser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = cronparser.parse(cronExpression);

        final var constraints = DefaultFixedWindowPlanningConstraints.builder()
                .withIdentity("foo")
                .withDuration(Duration.ofMinutes(60))
                .withZone("NL")
                .withStart(now)
                .withEnd(now.plusHours(6))
                .withFallbackCronExpression(cron)
                .withTimeZoneId(ZoneId.of("UTC"))
                .build();

        ZonedDateTime nextExecutionTime = defaultCarbonIntensityScheduler.getNextExecutionTime(constraints);
        assertThat(nextExecutionTime).isNotNull();
        assertThat(nextExecutionTime).isAfter(now.minusMinutes(1));
        assertThat(nextExecutionTime).isBefore(now.plusDays(1));
    }


    @Test
    void shouldScheduleOnFirstMonday() {
        final var parser = new CarbonIntensityJsonParser();
        final var carbonIntensity = parser.parse(ClassLoader.getSystemResourceAsStream("day-ahead-20240824-Z.json"));

        when(carbonIntensityDataFetcher.fetchCarbonIntensity(any()))
                .thenReturn(carbonIntensity);

        ZonedDateTime now = ZonedDateTime.now();

        String cronExpression = "0 0 * * *";
        CronParser cronparser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = cronparser.parse(cronExpression);

        final var constraints = DefaultFixedWindowPlanningConstraints.builder()
                .withIdentity("foo")
                .withDuration(Duration.ofMinutes(60))
                .withZone("NL")
                .withStart(now)
                .withEnd(now.plusHours(6))
                .withScheduledDayType(ScheduledDayType.MONDAY)
                .withFallbackCronExpression(cron)
                .withTimeZoneId(ZoneId.of("UTC"))
                .build();

        ZonedDateTime nextExecutionTime = defaultCarbonIntensityScheduler.getNextExecutionTime(constraints);
        assertThat(nextExecutionTime).isNotNull();
        assertThat(nextExecutionTime.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(nextExecutionTime).isAfter(now.minusMinutes(1));
        assertThat(nextExecutionTime).isBefore(now.plusDays(7));
    }

    @Test
    void shouldScheduleOnFirstDayOfMonth() {
        final var parser = new CarbonIntensityJsonParser();
        final var carbonIntensity = parser.parse(ClassLoader.getSystemResourceAsStream("day-ahead-20240824-Z.json"));

        when(carbonIntensityDataFetcher.fetchCarbonIntensity(any()))
                .thenReturn(carbonIntensity);

        ZonedDateTime now = ZonedDateTime.now();

        String cronExpression = "0 0 * * *";
        CronParser cronparser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = cronparser.parse(cronExpression);

        final var constraints = DefaultFixedWindowPlanningConstraints.builder()
                .withIdentity("foo")
                .withDuration(Duration.ofMinutes(60))
                .withZone("NL")
                .withStart(now)
                .withEnd(now.plusHours(6))
                .withScheduledDayType(ScheduledDayType.DAY_1)
                .withFallbackCronExpression(cron)
                .withTimeZoneId(ZoneId.of("UTC"))
                .build();

        ZonedDateTime nextExecutionTime = defaultCarbonIntensityScheduler.getNextExecutionTime(constraints);
        assertThat(nextExecutionTime).isNotNull();
        assertThat(nextExecutionTime.getDayOfMonth()).isEqualTo(1);
        assertThat(nextExecutionTime).isAfter(now.minusMinutes(1));
        assertThat(nextExecutionTime).isBefore(now.plusDays(31));
    }

    @Test
    void shouldNotScheduleOnSunday() {
        final var parser = new CarbonIntensityJsonParser();
        final var carbonIntensity = parser.parse(ClassLoader.getSystemResourceAsStream("day-ahead-20240824-Z.json"));

        when(carbonIntensityDataFetcher.fetchCarbonIntensity(any()))
                .thenReturn(carbonIntensity);

        ZonedDateTime date = ZonedDateTime.parse("2025-04-27T14:30+02:00");

        String cronExpression = "0 0 * * *";
        CronParser cronparser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = cronparser.parse(cronExpression);

        final var constraints = DefaultFixedWindowPlanningConstraints.builder()
                .withIdentity("foo")
                .withDuration(Duration.ofMinutes(60))
                .withZone("NL")
                .withStart(date)
                .withEnd(date.plusHours(6))
                .withScheduledDayType(ScheduledDayType.EVERY_WORKDAY)
                .withFallbackCronExpression(cron)
                .withTimeZoneId(ZoneId.of("UTC"))
                .build();

        ZonedDateTime nextExecutionTime = defaultCarbonIntensityScheduler.getNextExecutionTime(constraints);
        assertThat(nextExecutionTime).isNotNull();
        assertThat(nextExecutionTime.getDayOfWeek()).isNotEqualTo(DayOfWeek.SUNDAY);
        assertThat(nextExecutionTime).isAfter(date.minusMinutes(1));
        assertThat(nextExecutionTime).isBefore(date.plusDays(7));
    }

    @Test
    void shouldScheduleLastDayOfMonth() {
        final var parser = new CarbonIntensityJsonParser();
        final var carbonIntensity = parser.parse(ClassLoader.getSystemResourceAsStream("day-ahead-20240824-Z.json"));

        when(carbonIntensityDataFetcher.fetchCarbonIntensity(any()))
                .thenReturn(carbonIntensity);

        ZonedDateTime now = ZonedDateTime.now();

        String cronExpression = "0 0 * * *";
        CronParser cronparser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = cronparser.parse(cronExpression);

        final var constraints = DefaultFixedWindowPlanningConstraints.builder()
                .withIdentity("foo")
                .withDuration(Duration.ofMinutes(60))
                .withZone("NL")
                .withStart(now)
                .withEnd(now.plusHours(6))
                .withScheduledDayType(ScheduledDayType.DAY_31)
                .withFallbackCronExpression(cron)
                .withTimeZoneId(ZoneId.of("UTC"))
                .build();

        ZonedDateTime nextExecutionTime = defaultCarbonIntensityScheduler.getNextExecutionTime(constraints);
        assertThat(nextExecutionTime).isNotNull();
        assertThat(nextExecutionTime.getDayOfMonth()).isGreaterThanOrEqualTo(28);
        assertThat(nextExecutionTime).isAfter(now.minusMinutes(1));
        assertThat(nextExecutionTime).isBefore(now.plusDays(31));
    }

    @Test
    void shouldScheduleNextDay() {
        final var parser = new CarbonIntensityJsonParser();
        final var carbonIntensity = parser.parse(ClassLoader.getSystemResourceAsStream("day-ahead-20240824-Z.json"));

        when(carbonIntensityDataFetcher.fetchCarbonIntensity(any()))
                .thenReturn(carbonIntensity);

        ZonedDateTime now = ZonedDateTime.now();

        String cronExpression = "0 0 * * *";
        CronParser cronparser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = cronparser.parse(cronExpression);

        final var constraints = DefaultFixedWindowPlanningConstraints.builder()
                .withIdentity("foo")
                .withDuration(Duration.ofMinutes(60))
                .withZone("NL")
                .withStart(now)
                .withEnd(now.plusHours(6))
                .withFallbackCronExpression(cron)
                .withTimeZoneId(ZoneId.of("UTC"))
                .build();

        var constraintsNextDay = DefaultFixedWindowPlanningConstraints.from(constraints)
                .withStart(constraints.getStart().plusDays(1))
                .withEnd(constraints.getEnd().plusDays(1))
                .build();

        ZonedDateTime nextExecutionTime = defaultCarbonIntensityScheduler.getNextExecutionTime(constraintsNextDay);
        assertThat(nextExecutionTime).isNotNull();
        assertThat(nextExecutionTime).isAfter(now.plusDays(1).minusMinutes(1));
        assertThat(nextExecutionTime).isBefore(now.plusDays(2));
    }
}
