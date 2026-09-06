package io.carbonintensity.scheduler.observability;

/**
 * Which top-level scheduling strategy made a job's decision, recorded on every {@link DecisionTimelineEntry}.
 * <p>
 * These are the only two adopter-facing strategies a {@link io.carbonintensity.scheduler.GreenScheduled} job (or a
 * programmatic job) can be configured with - the internal cron/plain-interval mechanisms a job falls back to when no
 * carbon-aware slot is available are not separate strategies of their own, just a {@link DecisionReason} under
 * whichever of these two the job is actually configured as.
 */
public enum DecisionStrategy {

    /**
     * The job is configured with {@code fixedWindow()} - a carbon-aware slot within a daily window, falling back to
     * a configured cron expression when none is available.
     */
    FIXED_WINDOW,

    /**
     * The job is configured with {@code successive()} - a carbon-aware slot honoring a minimum/maximum gap since the
     * last execution, falling back to plain interval spacing when none is available. Programmatic jobs (
     * {@link io.carbonintensity.scheduler.Scheduler#newJob}) are always this strategy.
     */
    SUCCESSIVE

}
