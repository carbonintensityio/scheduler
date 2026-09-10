package io.carbonintensity.scheduler.runtime;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

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
     * Disabled by default: jobs schedule independently and may land on the exact same slot.
     */
    public static final int DEFAULT_MAX_CONCURRENT_PER_SLOT = 0;

    /**
     * Start of the daily window within which the carbon-impact batch (see {@code CarbonImpactBatchTrigger}) picks
     * its own, per-instance jittered moment - spreading load from many independent deployments instead of everyone
     * calling carbonintensity-api at the exact same time.
     */
    public static final LocalTime DEFAULT_CARBON_IMPACT_BATCH_WINDOW_START = LocalTime.of(2, 0);
    /**
     * End of the daily carbon-impact batch window, exclusive.
     */
    public static final LocalTime DEFAULT_CARBON_IMPACT_BATCH_WINDOW_END = LocalTime.of(6, 0);
    /**
     * Backoff delays between retries of a failed carbonintensity-api call during the carbon-impact batch, for
     * transient failures within the same run. The number of entries is the number of retries.
     */
    public static final List<Duration> DEFAULT_CARBON_IMPACT_RETRY_BACKOFFS = List.of(
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30));
    /**
     * How many days a job/day combination that still failed after all retries is kept as outstanding and retried on
     * a later batch run, before it is logged and abandoned - the cumulative carbon-savings total is a lower bound,
     * not a reconciled ledger, so this is not retried forever.
     */
    public static final int DEFAULT_CARBON_IMPACT_BACKLOG_WINDOW_DAYS = 7;

    private SchedulerDefaults() {
    }
}
