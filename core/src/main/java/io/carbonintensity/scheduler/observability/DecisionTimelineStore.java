package io.carbonintensity.scheduler.observability;

import java.util.List;

/**
 * Pluggable, retained storage for every job's decision timeline - answering
 * "why was this job shifted / is this a black box" unconditionally, for
 * every {@link io.carbonintensity.scheduler.GreenScheduled} and
 * programmatic job, no opt-in required. Unlike
 * {@code CarbonImpactHistoryStore} (short-lived, record-then-remove), this
 * is a long-lived ledger - eviction is its own job, no {@code remove} method.
 */
public interface DecisionTimelineStore {

    /**
     * Records one new decision for {@code identity}. This is an SPI, the
     * same override-hook shape as {@code CarbonIntensityApi} on
     * {@code SchedulerConfig#setCarbonIntensityApi} - the default,
     * {@code InMemoryDecisionTimelineStore}, keeps entries in memory,
     * pruned by the retention-days/max-entries config on
     * {@code SchedulerConfig}. Implementations own their own eviction.
     */
    void record(String identity, DecisionTimelineEntry entry);

    /**
     * A consumer needing the timeline to survive a restart, or to retain
     * more than the in-memory default, can implement and register this
     * via {@code SchedulerConfig#setDecisionTimelineStore}.
     *
     * @return an immutable snapshot of the entries retained for
     *         {@code identity}, oldest first
     */
    List<DecisionTimelineEntry> entriesFor(String identity);

}
