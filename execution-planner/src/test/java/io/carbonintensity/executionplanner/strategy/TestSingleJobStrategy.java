package io.carbonintensity.executionplanner.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.planner.Timeslot;
import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityJsonParser;

class TestSingleJobStrategy {

    private static final CarbonIntensityJsonParser ciParser = new CarbonIntensityJsonParser();

    @Test
    void testSingleJobStrategy() {

        CarbonIntensity carbonIntensity = loadCarbonIntensityFromFile("day-ahead-20240824-Z.json");
        ZonedDateTime ws = ZonedDateTime.parse("2024-08-27T00:00:00Z");
        ZonedDateTime we = ws.plusSeconds(60);
        Duration d = Duration.ofSeconds(60);

        SingleJobStrategy initialStrategy = new SingleJobStrategy();
        Timeslot timeslot = initialStrategy.bestTimeslot(ws, we, d, carbonIntensity);

        // check that we have a proper timeslot that fits the bill
        assertThat(timeslot).isNotNull();
        assertThat(Duration.between(timeslot.start(), timeslot.end())).isEqualByComparingTo(d);
        assertThat(timeslot.carbonIntensity()).isLessThan(new BigDecimal("1135"));
    }

    @Test
    void testRankedTimeslotsOrderedAscendingAndMatchesBestTimeslot() {
        CarbonIntensity carbonIntensity = loadCarbonIntensityFromFile("day-ahead-20240824-Z.json");
        ZonedDateTime ws = ZonedDateTime.parse("2024-08-27T00:00:00Z");
        ZonedDateTime we = ws.plusHours(6);
        Duration d = Duration.ofMinutes(30);

        SingleJobStrategy strategy = new SingleJobStrategy();
        List<Timeslot> ranked = strategy.rankedTimeslots(ws, we, d, carbonIntensity);

        assertThat(ranked).isNotEmpty();
        for (int i = 1; i < ranked.size(); i++) {
            assertThat(ranked.get(i - 1).carbonIntensity()).isLessThanOrEqualTo(ranked.get(i).carbonIntensity());
        }
        assertThat(ranked.get(0)).usingRecursiveComparison().isEqualTo(strategy.bestTimeslot(ws, we, d, carbonIntensity));
    }

    /**
     * {@link SingleJobStrategy#rankedTimeslots} sorts with {@link java.util.List#sort}, which is a stable
     * sort. On a tie in carbon intensity, this pins the deliberate (not accidental) tie-break: the
     * chronologically earliest tied slot stays ranked first, then the next earliest, and so on - because
     * {@link io.carbonintensity.executionplanner.planner.Timeslot#getTimeslots} generates candidate slots
     * in chronological order in the first place, and a stable sort never reorders equal elements.
     */
    @Test
    void testRankedTimeslotsBreaksTiesByChronologicalOrder() {
        // hourly data, resolution matches the 1-hour job duration and slot-generation resolution below,
        // so each generated timeslot lines up exactly with one data point: 100, 50, 50, 50, 200
        CarbonIntensity carbonIntensity = new CarbonIntensity();
        carbonIntensity.setZone("NL");
        carbonIntensity.setResolution(Duration.ofHours(1));
        carbonIntensity.setStart(Instant.parse("2024-01-01T00:00:00Z"));
        carbonIntensity.setEnd(Instant.parse("2024-01-01T05:00:00Z"));
        carbonIntensity.setData(List.of(
                new BigDecimal("100"), // 00:00 - not tied
                new BigDecimal("50"), // 01:00 - tied, chronologically first
                new BigDecimal("50"), // 02:00 - tied, chronologically second
                new BigDecimal("50"), // 03:00 - tied, chronologically third
                new BigDecimal("200") // 04:00 - not tied
        ));

        ZonedDateTime ws = ZonedDateTime.parse("2024-01-01T00:00:00Z");
        ZonedDateTime we = ws.plusHours(4);
        Duration d = Duration.ofHours(1);

        SingleJobStrategy strategy = new SingleJobStrategy(Duration.ofHours(1));
        List<Timeslot> ranked = strategy.rankedTimeslots(ws, we, d, carbonIntensity);

        assertThat(ranked).hasSize(5);
        assertThat(ranked).extracting(Timeslot::start).containsExactly(
                ws.plusHours(1), // 01:00, CI 50 - first among ties
                ws.plusHours(2), // 02:00, CI 50 - second among ties
                ws.plusHours(3), // 03:00, CI 50 - third among ties
                ws, // 00:00, CI 100
                ws.plusHours(4) // 04:00, CI 200
        );
    }

    @Test
    void testRankedTimeslotsSortsGenuineDataGapsLast() {
        // a single 30-minute data point at 00:00 - candidates starting at 01:00 and 02:00 have no overlapping
        // data at all, a genuine gap that must not be conflated with a real zero-intensity slot
        CarbonIntensity carbonIntensity = new CarbonIntensity();
        carbonIntensity.setZone("NL");
        carbonIntensity.setResolution(Duration.ofMinutes(30));
        carbonIntensity.setStart(Instant.parse("2024-01-01T00:00:00Z"));
        carbonIntensity.setEnd(Instant.parse("2024-01-01T00:30:00Z"));
        carbonIntensity.setData(List.of(new BigDecimal("50")));

        ZonedDateTime ws = ZonedDateTime.parse("2024-01-01T00:00:00Z");
        ZonedDateTime we = ws.plusHours(2);
        Duration d = Duration.ofHours(1);

        SingleJobStrategy strategy = new SingleJobStrategy(Duration.ofHours(1));
        List<Timeslot> ranked = strategy.rankedTimeslots(ws, we, d, carbonIntensity);

        assertThat(ranked).hasSize(3);
        // the one slot overlapping actual data sorts first; the two with no overlapping data at all sort last,
        // rather than throwing a NullPointerException or being indistinguishable from a real zero
        assertThat(ranked.get(0).carbonIntensity()).isNotNull();
        assertThat(ranked.get(1).carbonIntensity()).isNull();
        assertThat(ranked.get(2).carbonIntensity()).isNull();
    }

    private CarbonIntensity loadCarbonIntensityFromFile(String fileName) {
        return ciParser.parse(ClassLoader.getSystemResourceAsStream(fileName));
    }

}
