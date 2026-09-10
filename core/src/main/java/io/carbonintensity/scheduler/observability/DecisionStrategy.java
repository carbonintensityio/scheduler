package io.carbonintensity.scheduler.observability;

/**
 * Which top-level strategy made a job's decision, recorded on every
 * {@link DecisionTimelineEntry}.
 * <p>
 * The only two adopter-facing strategies a job (scheduled or programmatic)
 * can be configured with. Internal cron/plain-interval fallbacks aren't
 * separate strategies - just a {@link DecisionReason} under one of these two.
 */
public enum DecisionStrategy {

    /**
     * The job is configured with {@code fixedWindow()} - a carbon-aware slot
     * within a daily window, falling back to a configured cron expression
     * when none is available.
     */
    FIXED_WINDOW,

    /**
     * The job is configured with {@code successive()} - a carbon-aware slot
     * honoring a minimum/maximum gap since the last execution, falling back
     * to plain interval spacing when none is available. Programmatic jobs
     * ({@link io.carbonintensity.scheduler.Scheduler#newJob}) are always
     * this strategy.
     */
    SUCCESSIVE

}
