package io.carbonintensity.scheduler.observability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The outcome of one day's retrospective carbon-impact calculation for a single job.
 * <p>
 * Computed by the internal daily batch (see {@code CarbonImpactBatchTrigger}), never for the current day - see
 * {@link GreenObserved#carbonImpact()}.
 *
 * @param day the day these figures were computed for, always in the past
 * @param impactGrams the actual carbon impact of the job's run(s) on {@code day}, in grams CO2eq
 * @param savingsGrams the savings versus the job's naive baseline for {@code day}, in grams CO2eq; can be negative
 *        if the naive baseline would, for that day, have been better than the carbon-aware choice
 * @param computedAt when this result was computed, for freshness/staleness tracking
 */
public record CarbonImpactResult(LocalDate day, double impactGrams, double savingsGrams, Instant computedAt) {

    public CarbonImpactResult {
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(computedAt, "ComputedAt cannot be null");
    }

}
