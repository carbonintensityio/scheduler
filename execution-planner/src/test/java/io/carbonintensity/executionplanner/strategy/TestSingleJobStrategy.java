package io.carbonintensity.executionplanner.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;

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

    private CarbonIntensity loadCarbonIntensityFromFile(String fileName) {
        return ciParser.parse(ClassLoader.getSystemResourceAsStream(fileName));
    }

}
