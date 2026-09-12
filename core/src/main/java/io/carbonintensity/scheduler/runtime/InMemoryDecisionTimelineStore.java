package io.carbonintensity.scheduler.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.carbonintensity.scheduler.observability.DecisionTimelineEntry;
import io.carbonintensity.scheduler.observability.DecisionTimelineStore;

/**
 * The default {@link DecisionTimelineStore}: entries kept purely in
 * memory, lost on restart, bounded per job by a retention window and a
 * hard entry-count cap - whichever is hit first evicts the oldest entries.
 * <p>
 * Unlike {@code InMemoryCarbonImpactHistoryStore} (an external batch
 * process removes its processed windows), this store must self-prune.
 */
public final class InMemoryDecisionTimelineStore implements DecisionTimelineStore {

    private final Clock clock;
    private final Duration retention;
    private final int maxEntriesPerJob;
    private final ConcurrentMap<String, Timeline> timelinesByIdentity = new ConcurrentHashMap<>();

    public InMemoryDecisionTimelineStore(Clock clock, Duration retention, int maxEntriesPerJob) {
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
        this.retention = Objects.requireNonNull(retention, "Retention cannot be null");
        if (maxEntriesPerJob <= 0) {
            throw new IllegalArgumentException("Max entries per job must be positive");
        }
        this.maxEntriesPerJob = maxEntriesPerJob;
    }

    @Override
    public void record(String identity, DecisionTimelineEntry entry) {
        timelinesByIdentity.computeIfAbsent(identity, id -> new Timeline())
                .record(entry, clock.instant().minus(retention), maxEntriesPerJob);
    }

    @Override
    public List<DecisionTimelineEntry> entriesFor(String identity) {
        Timeline timeline = timelinesByIdentity.get(identity);
        return timeline == null ? List.of() : timeline.snapshot();
    }

    /**
     * One job's decision entries, kept sorted by {@code fireTime}.
     * Synchronized rather than lock-free: recording happens at most once
     * per second per job (the scheduler's own trigger-check tick), so
     * contention is a non-issue and a plain {@link ArrayList} behind a
     * monitor is simpler to reason about than a concurrent alternative.
     */
    private static final class Timeline {

        private static final Comparator<DecisionTimelineEntry> BY_FIRE_TIME = Comparator
                .comparing(DecisionTimelineEntry::fireTime);

        private final List<DecisionTimelineEntry> entries = new ArrayList<>();

        /**
         * {@link DecisionTimelineStore#record} has no ordering precondition,
         * so entries go in at their sorted position, not appended - eviction
         * only inspects the front, correct only if that's always the true
         * oldest entry. {@link SimpleScheduler} only calls this in fireTime
         * order already, so this is belt-and-braces, not a fix for an
         * observed bug - see {@code staysCorrectWhenEntriesArriveOutOfOrder}.
         */
        synchronized void record(DecisionTimelineEntry entry, Instant cutoff, int maxEntries) {
            int insertionPoint = Collections.binarySearch(entries, entry, BY_FIRE_TIME);
            entries.add(insertionPoint >= 0 ? insertionPoint : -insertionPoint - 1, entry);
            while (!entries.isEmpty() && entries.get(0).fireTime().isBefore(cutoff)) {
                entries.remove(0);
            }
            while (entries.size() > maxEntries) {
                entries.remove(0);
            }
        }

        synchronized List<DecisionTimelineEntry> snapshot() {
            return List.copyOf(entries);
        }
    }

}
