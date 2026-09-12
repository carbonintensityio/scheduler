package io.carbonintensity.scheduler.runtime;

import java.time.Duration;

import io.carbonintensity.scheduler.ConcurrentExecution;

/**
 * Default configuration of a scheduler
 */
public final class SchedulerDefaults {

    public static final Duration DEFAULT_INITIAL_MAXIMUM_DELAY = Duration.ofSeconds(0);
    public static final Duration DEFAULT_SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(30);
    public static final Duration DEFAULT_OVERDUE_GRACE_PERIOD = Duration.ofSeconds(30);
    public static final ConcurrentExecution DEFAULT_CONCURRENT_EXECUTION = ConcurrentExecution.PROCEED;
    public static final Duration DEFAULT_DURATION = Duration.ofSeconds(1);
    public static final String DEFAULT_API_URL = "https://api.carbonintensity.io";
    public static final int DEFAULT_NUMBER_OF_JOB_EXECUTORS = 10;
    /**
     * Disabled by default: jobs schedule independently and may land on the
     * exact same slot.
     */
    public static final int DEFAULT_MAX_CONCURRENT_PER_SLOT = 0;

    /**
     * How many days of decision-timeline entries the default in-memory
     * store retains per job.
     */
    public static final int DEFAULT_DECISION_TIMELINE_RETENTION_DAYS = 30;

    /**
     * Hard cap on decision-timeline entries retained per job, regardless of
     * age - a safety net against a pathologically high-frequency job
     * accumulating unbounded memory within the retention window.
     */
    public static final int DEFAULT_DECISION_TIMELINE_MAX_ENTRIES_PER_JOB = 1000;

    private SchedulerDefaults() {
    }
}
