package io.carbonintensity.scheduler.runtime;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.OptionalDouble;

import io.carbonintensity.executionplanner.spi.PlannedExecution;
import io.carbonintensity.scheduler.observability.DecisionReason;

/**
 * What a trigger decided on a single {@code evaluate()} call that led to a real fire: why this particular moment
 * was chosen, and the carbon-intensity value behind that choice, when one was actually known.
 * <p>
 * Deliberately not public API - {@link io.carbonintensity.scheduler.observability.DecisionTimelineEntry} is the
 * public-facing shape this feeds into. The two fields are always constructed together, atomically: a trigger that
 * mutated a reason field and an intensity-value field independently could fall back from a carbon-aware fire to a
 * plain cron/interval one and leave the previous, unrelated fire's real intensity value attached to the new
 * fallback reason. {@link #from(PlannedExecution, DecisionReason)} covers the carbon-aware path; fallback triggers
 * construct a {@code DecisionOutcome} directly, always pairing their reason with {@link OptionalDouble#empty()} -
 * never an inherited value.
 */
record DecisionOutcome(DecisionReason reason, OptionalDouble intensityValue) {

    DecisionOutcome {
        Objects.requireNonNull(reason, "Reason cannot be null");
        Objects.requireNonNull(intensityValue, "IntensityValue cannot be null - use OptionalDouble.empty()");
    }

    /**
     * Pulls the intensity straight from the {@link PlannedExecution} the planner already built for this fire,
     * rather than re-deriving it or leaving it to be filled in separately.
     */
    static DecisionOutcome from(PlannedExecution plannedExecution, DecisionReason reason) {
        Objects.requireNonNull(plannedExecution, "PlannedExecution cannot be null");
        BigDecimal intensity = plannedExecution.intensityValue().orElse(null);
        return new DecisionOutcome(reason,
                intensity == null ? OptionalDouble.empty() : OptionalDouble.of(intensity.doubleValue()));
    }
}
