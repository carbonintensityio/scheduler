package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;

/**
 * Example-based tests for {@link CarbonImpactCalculator#weightedImpact}, alongside the property-based tests in
 * {@link CarbonImpactCalculatorPropertiesTest}.
 */
class CarbonImpactCalculatorTest {

    private static final Instant DAY_START = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void windowEntirelyWithinOneBucketUsesThatBucketsValueDirectly() {
        // buckets: [00:00-01:00)=50, [01:00-02:00)=100, [02:00-03:00)=200
        // window 01:00-01:15 (15 minutes = 0.25h) is inside bucket 1, valued at 100
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(50), BigDecimal.valueOf(100), BigDecimal.valueOf(200));

        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity,
                DAY_START.plus(Duration.ofHours(1)), DAY_START.plus(Duration.ofHours(1)).plusSeconds(900));

        assertThat(impact).isEqualByComparingTo("25"); // 0.25h * 100
    }

    @Test
    void windowSpanningTwoWholeBucketsSumsBothInFull() {
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(50), BigDecimal.valueOf(100), BigDecimal.valueOf(200));

        // buckets 1 ([01:00-02:00)=100) and 2 ([02:00-03:00)=200), each counted in full
        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity,
                DAY_START.plus(Duration.ofHours(1)), DAY_START.plus(Duration.ofHours(3)));

        assertThat(impact).isEqualByComparingTo("300"); // 1h*100 + 1h*200
    }

    @Test
    void windowStraddlingABoundaryWeightsEachSideProportionally() {
        // 01:45-02:15: 15 min (0.25h) of bucket 1 (value 100), 15 min (0.25h) of bucket 2 (value 200)
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(50), BigDecimal.valueOf(100), BigDecimal.valueOf(200));

        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity,
                DAY_START.plus(Duration.ofHours(1)).plusSeconds(2700), DAY_START.plus(Duration.ofHours(2)).plusSeconds(900));

        assertThat(impact).isEqualByComparingTo("75"); // 0.25*100 + 0.25*200
    }

    @Test
    void zeroDurationWindowHasNoImpact() {
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(100));
        Instant instant = DAY_START.plus(Duration.ofHours(5));

        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity, instant, instant);

        assertThat(impact).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void invertedWindowHasNoImpact() {
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(100));
        Instant a = DAY_START.plus(Duration.ofHours(5));
        Instant b = DAY_START.plus(Duration.ofHours(6));

        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity, b, a);

        assertThat(impact).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void windowEntirelyBeforeTheDataRangeHasNoImpact() {
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(100));

        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity,
                DAY_START.minus(Duration.ofHours(2)), DAY_START.minus(Duration.ofHours(1)));

        assertThat(impact).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void windowEntirelyAfterTheDataRangeHasNoImpact() {
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(100)); // one bucket: [DAY_START, DAY_START+1h)

        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity,
                DAY_START.plus(Duration.ofHours(5)), DAY_START.plus(Duration.ofHours(6)));

        assertThat(impact).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void windowPartiallyOverlappingTheStartOfTheDataRangeOnlyCountsTheOverlap() {
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(100));

        // starts 30 minutes before the data range, ends 30 minutes into it
        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity,
                DAY_START.minusSeconds(1800), DAY_START.plusSeconds(1800));

        assertThat(impact).isEqualByComparingTo("50"); // only the 0.5h that overlaps bucket 0
    }

    @Test
    void emptyDataProducesZeroImpact() {
        CarbonIntensity intensity = new CarbonIntensity();
        intensity.setStart(DAY_START);
        intensity.setResolution(Duration.ofHours(1));
        intensity.setData(List.of());

        BigDecimal impact = CarbonImpactCalculator.weightedImpact(intensity,
                DAY_START, DAY_START.plus(Duration.ofHours(1)));

        assertThat(impact).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void toleratesAnInstantWindowExactlyOnTheDataEnd() {
        // regression guard: an off-by-one in the loop/overlap bounds could throw rather than just report zero
        CarbonIntensity intensity = hourlyIntensity(BigDecimal.valueOf(100));
        Instant dataEnd = DAY_START.plus(Duration.ofHours(1));

        assertThatCode(() -> CarbonImpactCalculator.weightedImpact(intensity, dataEnd, dataEnd.plusSeconds(1)))
                .doesNotThrowAnyException();
    }

    private static CarbonIntensity hourlyIntensity(BigDecimal... hourlyValues) {
        CarbonIntensity intensity = new CarbonIntensity();
        intensity.setStart(DAY_START);
        intensity.setResolution(Duration.ofHours(1));
        intensity.setData(List.of(hourlyValues));
        return intensity;
    }
}
