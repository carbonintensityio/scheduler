package io.carbonintensity.executionplanner.planner;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityJsonParser;

class TestTimeslot {
    static CarbonIntensity carbonIntensity;

    @BeforeAll
    public static void beforeAll() {
        CarbonIntensityJsonParser parser = new CarbonIntensityJsonParser();
        carbonIntensity = parser.parse(
                ClassLoader.getSystemResourceAsStream("day-ahead-20240824-Z.json"));
    }

    @Test
    void testFullDayInSeconds() {
        ZonedDateTime ws = ZonedDateTime.parse("2024-08-27T00:00:00Z");
        ZonedDateTime we = ZonedDateTime.parse("2024-08-28T00:00:00Z");

        List<Timeslot> timeslots = Timeslot.getTimeslots(ws, we, ofMinutes(60), ofSeconds(1), carbonIntensity);
        // should generate a timeslot for each second in the day
        assertThat(timeslots).hasSize(24 * 60 * 60 + 1);
        // check that the 3rd timeslot is actually the third second of the day
        assertThat(timeslots.get(2).start()).hasToString("2024-08-27T00:00:02Z");

    }

    @Test
    void testFullDayInQuarters() {
        ZonedDateTime ws = ZonedDateTime.parse("2024-08-27T00:00:00Z");
        ZonedDateTime we = ZonedDateTime.parse("2024-08-28T00:00:00Z");

        List<Timeslot> timeslots = Timeslot.getTimeslots(ws, we, ofMinutes(60), ofMinutes(15), carbonIntensity);
        // should generate a timeslot for each quarter-hour in the day
        assertThat(timeslots).hasSize(24 * 4 + 1);
        // check that the 12th timeslot is actually the 3rd hour of the day
        assertThat(timeslots.get(12).start()).hasToString("2024-08-27T03:00Z");
    }

    @Test
    void testDurationShorterThanWindowShouldWork() {
        // allow a 1-minute window
        ZonedDateTime ws = ZonedDateTime.parse("2024-08-27T00:00:00Z");
        ZonedDateTime we = ZonedDateTime.parse("2024-08-27T00:01:00Z");

        // but generate timeslots with a granularity of 1 hour
        List<Timeslot> timeslots = Timeslot.getTimeslots(ws, we, ofMinutes(60), ofHours(1), carbonIntensity);
        // should give exactly one slot
        assertThat(timeslots).hasSize(1);
    }

    @Test
    void testZeroWindow() {
        // allow a 0 window
        ZonedDateTime ws = ZonedDateTime.parse("2024-08-27T00:00:00Z");
        ZonedDateTime we = ZonedDateTime.parse("2024-08-27T00:00:00Z");

        // but generate timeslots with a granularity of 1 hour
        List<Timeslot> timeslots = Timeslot.getTimeslots(ws, we, ofMinutes(60), ofHours(1), carbonIntensity);
        // should give exactly one slot
        assertThat(timeslots).hasSize(1);
    }
}
