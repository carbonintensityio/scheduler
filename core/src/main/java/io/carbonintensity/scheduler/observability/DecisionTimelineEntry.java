package io.carbonintensity.scheduler.observability;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One firing of a job: when, why, and (when known) the carbon intensity.
 *
 * @param fireTime when the job actually fired
 * @param strategy which top-level strategy the job is configured with
 * @param reason why this particular moment was chosen
 * @param intensityValue the carbon intensity at {@code fireTime}, when known
 */
public record DecisionTimelineEntry(Instant fireTime, DecisionStrategy strategy, DecisionReason reason,
        OptionalDouble intensityValue) {

    public DecisionTimelineEntry {
        Objects.requireNonNull(fireTime, "FireTime cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
        Objects.requireNonNull(intensityValue, "IntensityValue cannot be null - use OptionalDouble.empty()");
    }

    /**
     * An {@link Instant}, not a {@link java.time.ZonedDateTime} - which zone
     * to render it in is a display concern (a log line, a docs example), not
     * something to bake into the stored data, consistent with
     * {@code CarbonImpactResult}.
     */
    @Override
    public Instant fireTime() {
        return fireTime;
    }

    /**
     * Genuinely absent for some {@link DecisionReason}s: a plain fallback
     * (cron or interval spacing) isn't driven by a carbon-intensity value at
     * all, so this is never a sentinel like {@code -1} or {@code NaN} -
     * always a real absence.
     */
    @Override
    public OptionalDouble intensityValue() {
        return intensityValue;
    }

}
