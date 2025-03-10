package io.carbonintensity.scheduler.runtime.impl.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.ZonedCarbonIntensityPeriod;

class TestCarbonIntensityFileApi {

    ZonedCarbonIntensityPeriod zonedPeriod;
    ZonedDateTime startTime = ZonedDateTime.now();
    ZonedDateTime endTime = startTime.plusHours(24);
    CarbonIntensityFileApi fileApi = new CarbonIntensityFileApi();

    @BeforeEach
    void setUp() {
        zonedPeriod = mock(ZonedCarbonIntensityPeriod.class);
        when(zonedPeriod.getZone()).thenReturn("NL");
        when(zonedPeriod.getStartTime()).thenReturn(startTime);
        when(zonedPeriod.getEndTime()).thenReturn(endTime);
    }

    @Test
    void whenZoneIsValid_thenCompleteWithData() {
        var data = fileApi.getCarbonIntensity(zonedPeriod).join();
        assertThat(data).isNotNull();
        assertThat(data.getZone()).isEqualTo(zonedPeriod.getZone());
        assertThat(data.getStart()).isEqualTo(startTime.toInstant().truncatedTo(ChronoUnit.HOURS));
        assertThat(data.getEnd()).isEqualTo(endTime.toInstant().truncatedTo(ChronoUnit.HOURS));
    }

    @Test
    void whenZoneIsUnknown_thenCompleteWithDefaultData() {
        when(zonedPeriod.getZone()).thenReturn("xy");
        var data = fileApi.getCarbonIntensity(zonedPeriod).join();
        assertThat(data).isNotNull();
        assertThat(data.getZone()).isEqualTo("xy");
    }

}
