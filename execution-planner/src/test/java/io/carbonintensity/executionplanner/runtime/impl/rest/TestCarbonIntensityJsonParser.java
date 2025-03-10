package io.carbonintensity.executionplanner.runtime.impl.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;

class TestCarbonIntensityJsonParser {

    CarbonIntensityJsonParser parser = new CarbonIntensityJsonParser();
    CarbonIntensity carbonIntensity;

    @BeforeEach
    public void setUp() {
        var startDate = Instant.parse("2024-09-20T08:00:00Z");
        carbonIntensity = new CarbonIntensity();
        carbonIntensity.setStart(startDate);
        carbonIntensity.setEnd(startDate.plus(Duration.ofDays(1)));
        carbonIntensity.setResolution(Duration.ofHours(1));
        carbonIntensity.getData().add(BigDecimal.valueOf(1000L));
        carbonIntensity.setZone("NL");
    }

    @Test
    void givenInputStream_whenValidJson_thenParseJson() {
        var jsonString = parser.toJson(carbonIntensity);
        var parsedData = parser.parse(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)));

        assertThat(parsedData)
                .usingRecursiveComparison()
                .isEqualTo(carbonIntensity);
    }

    @Test
    void givenCarbonIntensityInstance_whenConvertingToJsonString_thenCreateValidJsonString() {
        var jsonString = parser.toJson(carbonIntensity);
        assertThat(jsonString).isEqualTo(
                "{\"start\":\"2024-09-20T08:00:00Z\",\"end\":\"2024-09-21T08:00:00Z\",\"resolution\":\"PT1H\",\"zone\":\"NL\",\"data\":[1000]}");
    }

}
