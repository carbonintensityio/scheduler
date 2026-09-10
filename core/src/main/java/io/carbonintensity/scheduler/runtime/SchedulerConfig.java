package io.carbonintensity.scheduler.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApiConfig;
import io.carbonintensity.executionplanner.spi.CarbonIntensityApi;
import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.Scheduler;
import io.carbonintensity.scheduler.observability.CarbonImpactHistoryStore;
import io.carbonintensity.scheduler.spi.JobInstrumenter;

/**
 * Configuration class for the scheduler, defining various settings that control its behavior.
 * <p>
 * This class provides configuration options such as enabling/disabling the scheduler,
 * setting execution grace periods, defining job execution thread counts, and specifying the scheduler's start mode.
 * </p>
 *
 * <p>
 * The {@link StartMode} enum defines the different modes the scheduler can start in:
 * <ul>
 * <li>{@code NORMAL} - Starts only if scheduled methods or jobs exist.</li>
 * <li>{@code FORCED} - Always starts, even if no scheduled jobs exist.</li>
 * <li>{@code HALTED} - Starts but remains paused until manually resumed.</li>
 * </ul>
 * </p>
 *
 * @see GreenScheduled
 * @see Scheduler
 * @see CarbonIntensityApiConfig
 */
public class SchedulerConfig {

    /**
     * If schedulers are enabled.
     */
    private boolean enabled = true;

    private CarbonIntensityApiConfig carbonIntensityApiConfig;

    private CarbonIntensityApi carbonIntensityApi;

    /**
     * Pluggable storage for carbon-impact execution windows. {@code null} (the default) uses an in-memory
     * implementation - lost on restart, see {@link CarbonImpactHistoryStore}'s own Javadoc.
     */
    private CarbonImpactHistoryStore carbonImpactHistoryStore;

    private JobInstrumenter jobInstrumenter;

    private Clock clock = Clock.systemDefaultZone();

    /**
     * Scheduled task will be flagged as overdue if the next execution time is exceeded by this period.
     */
    private Duration overdueGracePeriod = SchedulerDefaults.DEFAULT_OVERDUE_GRACE_PERIOD;

    private Duration shutdownGracePeriod = SchedulerDefaults.DEFAULT_SHUTDOWN_GRACE_PERIOD;

    /**
     * Scheduler can be started in different modes. By default, the scheduler is not started unless a
     * {@link GreenScheduled} business method or programmatic job is registered.
     */
    private StartMode startMode = StartMode.NORMAL;

    private int jobExecutors = SchedulerDefaults.DEFAULT_NUMBER_OF_JOB_EXECUTORS;

    /**
     * Maximum number of jobs allowed to start at the exact same carbon-intensity slot within the same
     * zone. {@code 0} (the default) disables this: jobs schedule independently and may land on the same
     * moment. When set to a positive value, jobs beyond this limit for a given zone/slot are spread to
     * the next-best slot instead, but a job's configured window always takes priority over this limit.
     */
    private int maxConcurrentPerSlot = SchedulerDefaults.DEFAULT_MAX_CONCURRENT_PER_SLOT;

    /**
     * Start of the daily window within which the carbon-impact batch picks its own, per-instance jittered
     * moment to run in. See {@link SchedulerDefaults#DEFAULT_CARBON_IMPACT_BATCH_WINDOW_START}.
     */
    private LocalTime carbonImpactBatchWindowStart = SchedulerDefaults.DEFAULT_CARBON_IMPACT_BATCH_WINDOW_START;

    /**
     * End of the daily carbon-impact batch window (exclusive). See
     * {@link SchedulerDefaults#DEFAULT_CARBON_IMPACT_BATCH_WINDOW_END}.
     */
    private LocalTime carbonImpactBatchWindowEnd = SchedulerDefaults.DEFAULT_CARBON_IMPACT_BATCH_WINDOW_END;

    /**
     * Backoff delays between retries of a failed carbonintensity-api call during the carbon-impact batch. An empty
     * list means: fail immediately, without retrying within the same run.
     */
    private List<Duration> carbonImpactRetryBackoffs = SchedulerDefaults.DEFAULT_CARBON_IMPACT_RETRY_BACKOFFS;

    /**
     * How many days a job/day combination that failed after all retries stays outstanding and is retried on a
     * later batch run, before it is abandoned. {@code 0} disables catching up on later runs entirely.
     */
    private int carbonImpactBacklogWindowDays = SchedulerDefaults.DEFAULT_CARBON_IMPACT_BACKLOG_WINDOW_DAYS;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getJobExecutors() {
        return jobExecutors;
    }

    public void setJobExecutors(int jobExecutors) {
        if (jobExecutors < 1) {
            throw new IllegalArgumentException("Job executors cannot be less than 1");
        }
        this.jobExecutors = jobExecutors;
    }

    public int getMaxConcurrentPerSlot() {
        return maxConcurrentPerSlot;
    }

    public void setMaxConcurrentPerSlot(int maxConcurrentPerSlot) {
        if (maxConcurrentPerSlot < 0) {
            throw new IllegalArgumentException("Max concurrent per slot cannot be less than 0");
        }
        this.maxConcurrentPerSlot = maxConcurrentPerSlot;
    }

    public Duration getOverdueGracePeriod() {
        return overdueGracePeriod;
    }

    public void setOverdueGracePeriod(Duration overdueGracePeriod) {
        this.overdueGracePeriod = Objects.requireNonNull(overdueGracePeriod, "Overdue grace period cannot be null");
    }

    public Duration getShutdownGracePeriod() {
        return shutdownGracePeriod;
    }

    public void setShutdownGracePeriod(Duration shutdownGracePeriod) {
        this.shutdownGracePeriod = shutdownGracePeriod;
    }

    public StartMode getStartMode() {
        return startMode;
    }

    public void setStartMode(StartMode startMode) {
        this.startMode = Objects.requireNonNull(startMode, "Start mode cannot be null");
    }

    public CarbonIntensityApiConfig getCarbonIntensityApiConfig() {
        return carbonIntensityApiConfig;
    }

    public void setCarbonIntensityApiConfig(CarbonIntensityApiConfig carbonIntensityApiConfig) {
        this.carbonIntensityApiConfig = carbonIntensityApiConfig;
    }

    public enum StartMode {

        /**
         * The scheduler is not started unless a {@link GreenScheduled} business method or programmatic job is registered.
         */
        NORMAL,

        /**
         * The scheduler will be started even if no scheduled business methods or programmatic jobs are registered.
         * <p>
         * This is necessary for "pure" programmatic scheduling.
         */
        FORCED,

        /**
         * Just like the {@link #FORCED} mode but the scheduler will not start triggering jobs until {@link Scheduler#resume()}
         * is called.
         * <p>
         * This can be useful to run some initialization logic that needs to be performed before the scheduler starts.
         */
        HALTED
    }

    public CarbonIntensityApi getCarbonIntensityApi() {
        return carbonIntensityApi;
    }

    public void setCarbonIntensityApi(CarbonIntensityApi carbonIntensityApi) {
        this.carbonIntensityApi = carbonIntensityApi;
    }

    public CarbonImpactHistoryStore getCarbonImpactHistoryStore() {
        return carbonImpactHistoryStore;
    }

    public void setCarbonImpactHistoryStore(CarbonImpactHistoryStore carbonImpactHistoryStore) {
        this.carbonImpactHistoryStore = carbonImpactHistoryStore;
    }

    public JobInstrumenter getJobInstrumenter() {
        return jobInstrumenter;
    }

    public void setJobInstrumenter(JobInstrumenter jobInstrumenter) {
        this.jobInstrumenter = jobInstrumenter;
    }

    public Clock getClock() {
        return clock;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public LocalTime getCarbonImpactBatchWindowStart() {
        return carbonImpactBatchWindowStart;
    }

    public LocalTime getCarbonImpactBatchWindowEnd() {
        return carbonImpactBatchWindowEnd;
    }

    public void setCarbonImpactBatchWindow(LocalTime start, LocalTime end) {
        Objects.requireNonNull(start, "Carbon-impact batch window start cannot be null");
        Objects.requireNonNull(end, "Carbon-impact batch window end cannot be null");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "Carbon-impact batch window start must be before its end (a window spanning midnight is not supported)");
        }
        this.carbonImpactBatchWindowStart = start;
        this.carbonImpactBatchWindowEnd = end;
    }

    public List<Duration> getCarbonImpactRetryBackoffs() {
        return carbonImpactRetryBackoffs;
    }

    public void setCarbonImpactRetryBackoffs(List<Duration> carbonImpactRetryBackoffs) {
        this.carbonImpactRetryBackoffs = List.copyOf(
                Objects.requireNonNull(carbonImpactRetryBackoffs, "Carbon-impact retry backoffs cannot be null"));
    }

    public int getCarbonImpactBacklogWindowDays() {
        return carbonImpactBacklogWindowDays;
    }

    public void setCarbonImpactBacklogWindowDays(int carbonImpactBacklogWindowDays) {
        if (carbonImpactBacklogWindowDays < 0) {
            throw new IllegalArgumentException("Carbon-impact backlog window days cannot be less than 0");
        }
        this.carbonImpactBacklogWindowDays = carbonImpactBacklogWindowDays;
    }

}
