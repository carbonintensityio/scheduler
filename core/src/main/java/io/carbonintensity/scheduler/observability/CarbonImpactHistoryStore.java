package io.carbonintensity.scheduler.observability;

import java.time.Instant;
import java.util.List;

import io.carbonintensity.scheduler.runtime.InMemoryCarbonImpactHistoryStore;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;

/**
 * Pluggable storage for the wall-clock execution windows of {@code carbonImpact}-enabled jobs, awaiting processing
 * by the carbon-impact batch.
 * <p>
 * This is an SPI, the same override-hook shape as {@code CarbonIntensityApi} on
 * {@link SchedulerConfig#setCarbonIntensityApi}: the default, {@link InMemoryCarbonImpactHistoryStore}, keeps
 * windows purely in memory - if the application restarts before the batch runs, that day's executions are lost for
 * good (see {@code CarbonImpactBatchTrigger}), and the cumulative savings total is a lower bound, not a reconciled
 * ledger. A consumer that needs windows to survive a restart, or to keep a longer/queryable decision history than
 * the configured backlog window, can implement this interface against their own datastore and register it via
 * {@link SchedulerConfig#setCarbonImpactHistoryStore}.
 */
public interface CarbonImpactHistoryStore {

    /**
     * Records one successful execution's wall-clock start/end for {@code identity}.
     */
    void record(String identity, Instant start, Instant end);

    /**
     * @return an immutable snapshot of the windows currently recorded for {@code identity}
     */
    List<ExecutionWindow> windowsFor(String identity);

    /**
     * Removes windows that were successfully processed by the batch, so they are not counted again on a later run.
     * <p>
     * Implementations should match by identity/reference, not {@link ExecutionWindow#equals(Object)}: two distinct
     * executions can share an identical (start, end), and value-equality removal would then delete an unprocessed
     * window too - see {@link InMemoryCarbonImpactHistoryStore}'s identity-based removal for a worked example.
     */
    void remove(String identity, List<ExecutionWindow> processed);

}
