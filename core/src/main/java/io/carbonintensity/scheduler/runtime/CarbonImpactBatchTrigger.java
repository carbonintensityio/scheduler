package io.carbonintensity.scheduler.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The internal trigger driving the once-daily, retrospective carbon-impact batch (see
 * {@link io.carbonintensity.scheduler.observability.GreenObserved#carbonImpact()}).
 * <p>
 * Fires once per calendar day, at a fixed, per-instance jittered moment within the configured window (see
 * {@link SchedulerConfig#getCarbonImpactBatchWindowStart()}/{@link SchedulerConfig#getCarbonImpactBatchWindowEnd()}).
 * The jitter is picked once, when this trigger is created, and held for the process's lifetime - not re-randomized
 * every day - so that many independent deployments calling carbonintensity-api don't all do so at the exact same
 * moment, without the fire time jumping around unpredictably from one day to the next for any single instance.
 * <p>
 * Evaluated on every regular scheduler tick, like any other trigger - deliberately not a wall-clock-sleeping
 * background thread, so it can be driven deterministically by an injected {@link Clock} in tests, the same way every
 * other trigger in this class already is.
 */
final class CarbonImpactBatchTrigger extends SimpleScheduler.SimpleTrigger {

    static final String IDENTITY = "__green-observed-carbon-batch__";

    private final LocalTime windowStart;
    private final LocalTime windowEnd;
    private final Duration jitter;

    CarbonImpactBatchTrigger(Clock clock, LocalTime windowStart, LocalTime windowEnd, Duration jitter) {
        super(IDENTITY, clock, ZonedDateTime.now(clock), "CarbonImpactBatchTrigger");
        Objects.requireNonNull(windowStart, "Window start cannot be null");
        Objects.requireNonNull(windowEnd, "Window end cannot be null");
        Objects.requireNonNull(jitter, "Jitter cannot be null");
        if (!windowStart.isBefore(windowEnd)) {
            throw new IllegalArgumentException("Window start must be before its end");
        }
        Duration windowLength = Duration.between(windowStart, windowEnd);
        if (jitter.isNegative() || jitter.compareTo(windowLength) >= 0) {
            throw new IllegalArgumentException("Jitter must be within [0, " + windowLength + "), was " + jitter);
        }
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.jitter = jitter;
    }

    /**
     * @return a jitter, uniformly distributed within the given window, suitable for production use - not
     *         deterministic, do not use in tests
     */
    static Duration randomJitter(LocalTime windowStart, LocalTime windowEnd) {
        long windowMillis = Duration.between(windowStart, windowEnd).toMillis();
        long offsetMillis = windowMillis <= 0 ? 0 : ThreadLocalRandom.current().nextLong(windowMillis);
        return Duration.ofMillis(offsetMillis);
    }

    private ZonedDateTime targetFireTime(ZonedDateTime referenceDay) {
        return referenceDay.toLocalDate().atTime(windowStart).plus(jitter).atZone(referenceDay.getZone());
    }

    private boolean firedOnOrAfter(ZonedDateTime day) {
        ZonedDateTime last = lastFireTime;
        return last != null && !last.toLocalDate().isBefore(day.toLocalDate());
    }

    @Override
    ZonedDateTime evaluate(ZonedDateTime now) {
        ZonedDateTime target = targetFireTime(now);
        if (now.isBefore(target) || firedOnOrAfter(now)) {
            return null;
        }
        lastFireTime = now;
        return target;
    }

    @Override
    public Instant getNextFireTime() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime todayTarget = targetFireTime(now);
        if (!firedOnOrAfter(now) && now.isBefore(todayTarget)) {
            return todayTarget.toInstant();
        }
        return targetFireTime(now.plusDays(1)).toInstant();
    }

    @Override
    public boolean isOverdue() {
        return false;
    }

}
