package io.carbonintensity.executionplanner.planner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;

/**
 * A possible timeslot for a job to run, its duration should be equal to the job's duration.
 * Will probably overlap multiple CarbonIntensityPeriod instances.
 */
public class Timeslot {
    ZonedDateTime start;
    ZonedDateTime end;
    /**
     * {@code null} when no {@link CarbonIntensityPeriod} overlapped this slot at all - a genuine data gap, never
     * to be confused with a real zero-intensity slot.
     */
    BigDecimal carbonIntensity;

    public Timeslot(ZonedDateTime start, ZonedDateTime end, BigDecimal carbonIntensity) {
        this.start = start;
        this.end = end;
        this.carbonIntensity = carbonIntensity;
    }

    public ZonedDateTime start() {
        return start;
    }

    public ZonedDateTime end() {
        return end;
    }

    /**
     * @return the carbon-intensity value for this slot, or {@code null} if no data was available to compute one -
     *         never a sentinel like {@link BigDecimal#ZERO} standing in for "unknown"
     */
    public BigDecimal carbonIntensity() {
        return carbonIntensity;
    }

    /**
     * Generate a list of timeslots for a given period of time.
     * Currently for each starting second, a timeslot is generated.
     *
     * @param ws the start of the window to start the job in
     * @param we the end of the window to start the job in
     * @param timeslotDuration the duration of each timeslot
     * @param resolution the resolution of generating timeslots (e.g. a timeslot every minute)
     * @param carbonIntensity the carbon intensity data
     * @return a list of timeslots
     */
    public static List<Timeslot> getTimeslots(ZonedDateTime ws, ZonedDateTime we, Duration timeslotDuration,
            Duration resolution, CarbonIntensity carbonIntensity) {
        List<CarbonIntensityPeriod> periods = CarbonIntensityPeriod.of(carbonIntensity);

        List<Timeslot> timeslots = new ArrayList<>();
        ZonedDateTime s = ws;

        while (!s.isAfter(we)) { // allow equal for 0 windows
            ZonedDateTime e = s.plus(timeslotDuration);
            timeslots.add(new Timeslot(s, e, calculateCarbonIntensity(periods, s, e).orElse(null)));
            s = s.plus(resolution);
        }
        return timeslots;
    }

    /**
     * @return the summed carbon-intensity contribution of every {@link CarbonIntensityPeriod} overlapping
     *         {@code [start, end]}, or {@link Optional#empty()} if none overlapped at all - distinguishing a
     *         genuine data gap from a real zero, which {@link BigDecimal#ZERO} as a reduce identity could not
     */
    public static Optional<BigDecimal> calculateCarbonIntensity(List<CarbonIntensityPeriod> carbonIntensityInstants,
            ZonedDateTime start, ZonedDateTime end) {
        List<CarbonIntensityPeriod> overlapping = carbonIntensityInstants.stream()
                .filter(m -> m.contains(start.toInstant()) || m.contains(end.toInstant()))
                .toList();
        if (overlapping.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (CarbonIntensityPeriod ci : overlapping) {
            sum = sum.add(calculateCarbonIntensity(start, end, ci));
        }
        return Optional.of(sum);
    }

    private static BigDecimal calculateCarbonIntensity(ZonedDateTime start, ZonedDateTime end, CarbonIntensityPeriod ci) {
        Instant ciStart = ci.moment();
        Instant ciEnd = ciStart.plus(ci.resolution());
        if (start.toInstant().compareTo(ciStart) <= 0
                && end.toInstant().compareTo(ciEnd) >= 0) {
            return ci.value();
        }
        if (start.toInstant().compareTo(ciStart) >= 0 && start.toInstant().compareTo(ciEnd) <= 0) {
            long secsInCiPeriod;
            // job start in or on ci window
            if (end.toInstant().compareTo(ciEnd) <= 0) {
                // job ends in ci window
                secsInCiPeriod = Duration.between(start, end).getSeconds();
            } else {
                secsInCiPeriod = Duration.between(start.toInstant(), ciEnd).getSeconds();
            }
            return ci.value().divide(BigDecimal.valueOf(ci.resolution().getSeconds()), RoundingMode.HALF_EVEN)
                    .multiply(BigDecimal.valueOf(secsInCiPeriod));
        }
        //job ends in or on ci window, but does not start in it
        if (end.toInstant().compareTo(ciStart) >= 0 && end.toInstant().compareTo(ciEnd) <= 0) {
            long secsInCiPeriod = Duration.between(ciStart, end.toInstant()).getSeconds();
            return ci.value().divide(BigDecimal.valueOf(ci.resolution().getSeconds()), RoundingMode.HALF_EVEN)
                    .multiply(BigDecimal.valueOf(secsInCiPeriod));
        }

        return BigDecimal.ZERO;
    }

    @Override
    public String toString() {
        return "Timeslot{" +
                "start=" + start +
                ", end=" + end +
                ", carbonIntensity=" + carbonIntensity +
                '}';
    }
}
