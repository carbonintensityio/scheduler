package io.carbonintensity.scheduler.runtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.vavr.test.Arbitrary;
import io.vavr.test.Gen;
import io.vavr.test.Property;

/**
 * Property-based tests for {@link CarbonImpactCalculator#weightedImpact}, alongside the example-based tests in
 * {@link CarbonImpactCalculatorTest}.
 * <p>
 * The core invariant checked here is additivity across window-splitting: for any window and any point splitting it
 * in two, computing the impact of the whole window in one call must equal computing the impact of each half
 * separately and summing them. This is the property that actually validates the hour-bucket weighting logic in
 * general - the example-based tests hand-pick a few boundary shapes, but this holds regardless of how many buckets
 * the window crosses, how the split point lands relative to bucket boundaries, or how much data exists.
 * <p>
 * See {@code docs/adr/0002-vavr-test-over-jqwik.md} for why vavr-test rather than jqwik is used here.
 */
class CarbonImpactCalculatorPropertiesTest {

    private static final Instant DAY_START = Instant.parse("2026-09-01T00:00:00Z");

    // A window can extend before/after the data range too (the calculator must handle that gracefully, see the
    // example-based "entirely before/after the data range" tests) - this offset is deliberately allowed to go
    // negative and past a typical data range's length.
    private static final Arbitrary<Long> START_OFFSET_SECONDS = Gen.choose(-6L * 3600, 30L * 3600).arbitrary();
    // Each half of the window is 0 to 6 hours long, generated independently so the split point can land anywhere:
    // mid-bucket, exactly on a boundary, or make one half zero-length.
    private static final Arbitrary<Long> FIRST_SEGMENT_SECONDS = Gen.choose(0L, 6L * 3600).arbitrary();
    private static final Arbitrary<Long> SECOND_SEGMENT_SECONDS = Gen.choose(0L, 6L * 3600).arbitrary();
    // Between roughly 0 and 48 hourly buckets of data - Arbitrary.list's own size parameter drives the length.
    private static final Arbitrary<io.vavr.collection.List<BigDecimal>> BUCKETS = Arbitrary
            .list(Gen.choose(0, 100_000).map(BigDecimal::valueOf).arbitrary());

    @Test
    void splittingAWindowAndSummingEqualsComputingItWhole() {
        Property.def("weightedImpact(a,c) == weightedImpact(a,b) + weightedImpact(b,c) for any a<=b<=c")
                .forAll(BUCKETS, START_OFFSET_SECONDS, FIRST_SEGMENT_SECONDS, SECOND_SEGMENT_SECONDS)
                .suchThat(CarbonImpactCalculatorPropertiesTest::additivityHolds)
                .check()
                .assertIsSatisfied();
    }

    private static boolean additivityHolds(io.vavr.collection.List<BigDecimal> bucketValues, long startOffsetSeconds,
            long firstSegmentSeconds, long secondSegmentSeconds) {
        List<BigDecimal> data = bucketValues.toJavaList();
        if (data.isEmpty()) {
            return true; // nothing to check against, covered separately by the empty-data example test
        }
        CarbonIntensity intensity = hourlyIntensity(data);

        Instant a = DAY_START.plusSeconds(startOffsetSeconds);
        Instant b = a.plusSeconds(firstSegmentSeconds);
        Instant c = b.plusSeconds(secondSegmentSeconds);

        BigDecimal whole = CarbonImpactCalculator.weightedImpact(intensity, a, c);
        BigDecimal firstHalf = CarbonImpactCalculator.weightedImpact(intensity, a, b);
        BigDecimal secondHalf = CarbonImpactCalculator.weightedImpact(intensity, b, c);

        return isNegligibleDifference(whole, firstHalf.add(secondHalf));
    }

    /**
     * BigDecimal division in the calculator (MathContext.DECIMAL64) can introduce rounding differences on the
     * order of 1e-16 relative to the values involved between computing a window in one pass versus two - this
     * tolerance is many orders of magnitude looser than that, so it only ever accepts genuine floating-point noise,
     * never a real additivity violation.
     */
    private static boolean isNegligibleDifference(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(new BigDecimal("0.0000001")) < 0;
    }

    private static CarbonIntensity hourlyIntensity(List<BigDecimal> hourlyValues) {
        CarbonIntensity intensity = new CarbonIntensity();
        intensity.setStart(DAY_START);
        intensity.setResolution(Duration.ofHours(1));
        intensity.setData(hourlyValues);
        return intensity;
    }
}
