package io.carbonintensity.executionplanner.runtime.impl.rest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensityCache;

class TestCarbonIntensityCache {

    final String zone = "NL";
    Instant startTime;
    CarbonIntensity carbonIntensity;
    CarbonIntensityCache cache;

    @BeforeEach
    public void setup() {
        startTime = Instant.parse("2018-11-30T18:35:24.00Z");
        carbonIntensity = new CarbonIntensity();
        cache = new CarbonIntensityCache(Duration.ofHours(1));
    }

    @Test
    void givenPopulatedCache_whenKeyNotExpired_thenReturnValue() {
        var key = new CarbonIntensityCache.Key(startTime, zone);
        cache.put(key, carbonIntensity);
        assertThat(cache.get(key)).isPresent().hasValue(carbonIntensity);
    }

    @Test
    void whenGettingCacheValue_thenTruncateKeyTimeToHours() {
        var key = new CarbonIntensityCache.Key(startTime, zone);
        cache.put(key, carbonIntensity);
        assertThat(cache.get(key)).isPresent().hasValue(carbonIntensity);
        var timeWithDifferentMinutes = Instant.parse("2018-11-30T18:45:55.66Z");
        key = new CarbonIntensityCache.Key(timeWithDifferentMinutes, zone);
        assertThat(cache.get(key)).isPresent().hasValue(carbonIntensity);
    }

    @Test
    void givenCacheWithExpiredItem_whenGettingItemFromCache_thenReturnNull() {
        cache = new CarbonIntensityCache(Duration.ofMillis(1));
        var key = new CarbonIntensityCache.Key(startTime, zone);
        assertThat(cache.get(key)).isNotPresent();
        cache.put(key, carbonIntensity);
        waitAtMost(1, TimeUnit.SECONDS).until(() -> cache.get(key).isEmpty());
        assertThat(cache.get(key)).isNotPresent();
    }

}
