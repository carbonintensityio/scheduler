package io.carbonintensity.scheduler.observability;

import java.util.List;

import io.carbonintensity.scheduler.runtime.InMemoryDecisionTimelineStore;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;

/**
 * Pluggable, retained storage for every job's decision timeline - answering "why was this job shifted / is this a
 * black box" (unconditionally, for every {@link io.carbonintensity.scheduler.GreenScheduled} and programmatic job,
 * no opt-in required).
 * <p>
 * This is an SPI, the same override-hook shape as {@code CarbonIntensityApi} on
 * {@link SchedulerConfig#setCarbonIntensityApi}: the default, {@link InMemoryDecisionTimelineStore}, keeps entries
 * purely in memory, pruned by {@link SchedulerConfig#getDecisionTimelineRetentionDays()} and
 * {@link SchedulerConfig#getDecisionTimelineMaxEntriesPerJob()} - lost on restart, and bounded regardless of how
 * long the process runs. A consumer that needs the timeline to survive a restart, or to keep more than the default
 * in-memory retention, can implement this interface against their own datastore and register it via
 * {@link SchedulerConfig#setDecisionTimelineStore}.
 * <p>
 * Unlike {@code CarbonImpactHistoryStore} (a short-lived, record-then-remove staging area for the daily
 * carbon-impact batch), this store is a long-lived, retained ledger - retention/eviction is this store's own
 * internal responsibility, not driven by an external process, so there is deliberately no {@code remove} method.
 */
public interface DecisionTimelineStore {

    /**
     * Records one new decision for {@code identity}. Implementations are responsible for their own retention/
     * eviction policy.
     */
    void record(String identity, DecisionTimelineEntry entry);

    /**
     * @return an immutable snapshot of the entries currently retained for {@code identity}, oldest first
     */
    List<DecisionTimelineEntry> entriesFor(String identity);

}
