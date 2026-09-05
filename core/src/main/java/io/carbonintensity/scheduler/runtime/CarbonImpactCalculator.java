package io.carbonintensity.scheduler.runtime;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;

/**
 * Computes the duration x intensity carbon-impact proxy (CIIO-277, CIIO-278) for an execution window against a
 * day's actual-intensity data, weighting each overlapping resolution bucket (typically hourly) by the fraction of
 * the window that actually falls within it - so a window straddling a bucket boundary is not simply attributed to
 * whichever bucket it starts in.
 */
final class CarbonImpactCalculator {

    private static final BigDecimal ONE_HOUR_NANOS = BigDecimal.valueOf(Duration.ofHours(1).toNanos());

    private CarbonImpactCalculator() {
    }

    /**
     * @param intensity a day's actual-intensity data (gCO2eq/kWh per resolution-length bucket, starting at
     *        {@link CarbonIntensity#getStart()})
     * @param windowStart the execution window's start (inclusive)
     * @param windowEnd the execution window's end (exclusive)
     * @return the impact in grams CO2eq, assuming a nominal 1kW nameplate power - zero for a non-positive-length
     *         window, and only accounting for the portion of the window that actually overlaps {@code intensity}'s
     *         own range (a window extending outside it contributes nothing for that outside portion)
     */
    static BigDecimal weightedImpact(CarbonIntensity intensity, Instant windowStart, Instant windowEnd) {
        if (!windowEnd.isAfter(windowStart)) {
            return BigDecimal.ZERO;
        }

        Duration resolution = intensity.getResolution();
        List<BigDecimal> data = intensity.getData();
        Instant dataStart = intensity.getStart();

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < data.size(); i++) {
            Instant bucketStart = dataStart.plus(resolution.multipliedBy(i));
            Instant bucketEnd = bucketStart.plus(resolution);
            Instant overlapStart = laterOf(bucketStart, windowStart);
            Instant overlapEnd = earlierOf(bucketEnd, windowEnd);
            if (overlapEnd.isAfter(overlapStart)) {
                BigDecimal overlapHours = BigDecimal.valueOf(Duration.between(overlapStart, overlapEnd).toNanos())
                        .divide(ONE_HOUR_NANOS, MathContext.DECIMAL64);
                total = total.add(overlapHours.multiply(data.get(i)));
            }
        }
        return total;
    }

    private static Instant laterOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant earlierOf(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

}
