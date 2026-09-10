package io.carbonintensity.executionplanner.spi;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import io.carbonintensity.executionplanner.planner.Timeslot;

/**
 * The outcome of a successful {@code getNextExecutionTime} call: the
 * chosen fire time, and the carbon-intensity value of the timeslot it
 * was chosen from, when known.
 * <p>
 * {@link CarbonIntensityPlanner#getNextExecutionTime} may still return
 * {@code null} (no timeslot found) - this only wraps the success case.
 */
public record PlannedExecution(ZonedDateTime fireTime, Optional<BigDecimal> intensityValue) {

    public PlannedExecution {
        Objects.requireNonNull(fireTime, "FireTime cannot be null");
        Objects.requireNonNull(intensityValue, "IntensityValue cannot be null - use Optional.empty()");
    }

    /**
     * The single, shared conversion point every {@link CarbonIntensityPlanner}
     * implementation should use - so a {@code null}
     * {@link Timeslot#carbonIntensity()} (a genuine data gap) consistently
     * becomes an absent {@code intensityValue}, rather than each planner
     * re-deriving that mapping on its own.
     */
    public static PlannedExecution from(Timeslot timeslot) {
        return new PlannedExecution(timeslot.start(), Optional.ofNullable(timeslot.carbonIntensity()));
    }
}
