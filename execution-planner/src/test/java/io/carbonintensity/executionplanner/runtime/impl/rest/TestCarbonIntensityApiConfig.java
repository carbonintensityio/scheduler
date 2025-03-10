package io.carbonintensity.executionplanner.runtime.impl.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestCarbonIntensityApiConfig {

    CarbonIntensityApiConfig.Builder builder;

    @BeforeEach
    public void setup() {
        builder = new CarbonIntensityApiConfig.Builder();
    }

    @Test
    void testApiKey() {
        var config = builder
                .apiKey("apiKey")
                .build();

        assertThat(config.getApiKey()).isEqualTo("apiKey");
    }

    @Test
    void testApiUrl() {
        assertThat(builder
                .apiUrl("baseUrl")
                .build()
                .getApiUrl()).isEqualTo("baseUrl");
    }

    @ParameterizedTest
    @MethodSource("testEnabledParameters")
    void testEnabled(String apiKey, String apiUrl, boolean enabled) {
        assertThat(builder
                .apiKey(apiKey)
                .apiUrl(apiUrl)
                .build()
                .isEnabled())
                .isEqualTo(enabled);
    }

    private static Stream<Arguments> testEnabledParameters() {
        return Stream.of(
                Arguments.of(null, null, false),
                Arguments.of("", "", false),
                Arguments.of("null", null, false),
                Arguments.of(null, "null", false),
                Arguments.of("null", "null", true));
    }

    @Test
    void whenApiKeyAndApiUrlIsSet_thenEnableApi() {
        assertThat(builder
                .apiKey(null)
                .apiUrl("baseUrl")
                .build().isEnabled()).isFalse();
    }

}
