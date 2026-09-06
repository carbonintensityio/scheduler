package io.carbonintensity.executionplanner.spi;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import io.carbonintensity.executionplanner.planner.Timeslot;

/**
 * The outcome of a successful {@link CarbonIntensityPlanner#getNextExecutionTime} call: the chosen fire time, and
 * the carbon-intensity value of the timeslot it was chosen from, when known.
 * <p>
 * {@link CarbonIntensityPlanner#getNextExecutionTime} itself may still return {@code null} (no timeslot could be
 * found) - this type only wraps the successful case.
 */
public record PlannedExecution(ZonedDateTime fireTime, Optional<BigDecimal> intensityValue) {

    public PlannedExecution {
        Objects.requireNonNull(fireTime, "FireTime cannot be null");
        Objects.requireNonNull(intensityValue, "IntensityValue cannot be null - use Optional.empty()");
    }

    /**
     * The single, shared conversion point every {@link CarbonIntensityPlanner} implementation should use - so a
     * {@code null} {@link Timeslot#carbonIntensity()} (a genuine data gap) consistently becomes an absent
     * {@code intensityValue} here, rather than each planner re-deriving that mapping on its own.
     */
    public static PlannedExecution from(Timeslot timeslot) {
        return new PlannedExecution(timeslot.start(), Optional.ofNullable(timeslot.carbonIntensity()));
    }
}
