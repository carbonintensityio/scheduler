package io.carbonintensity.scheduler.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.carbonintensity.scheduler.observability.DecisionTimelineEntry;
import io.carbonintensity.scheduler.observability.DecisionTimelineStore;

/**
 * The default {@link DecisionTimelineStore}: entries kept purely in memory, lost on restart, bounded per job by
 * both a retention window and a hard entry-count cap - whichever is hit first evicts the oldest entries.
 * <p>
 * Unlike {@code InMemoryCarbonImpactHistoryStore} (which relies entirely on an external batch process removing
 * processed windows, with no cap of its own), this store must self-prune: it is a long-lived, always-growing ledger
 * with no external remover.
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
     * One job's decision entries, oldest first. Synchronized rather than a lock-free structure: recording happens at
     * most once per second per job (driven by the scheduler's own trigger-check tick), so contention is a non-issue
     * and a plain {@link ArrayDeque} behind a monitor is simpler to reason about than a concurrent alternative.
     */
    private static final class Timeline {

        private final Deque<DecisionTimelineEntry> entries = new ArrayDeque<>();

        synchronized void record(DecisionTimelineEntry entry, Instant cutoff, int maxEntries) {
            entries.addLast(entry);
            while (!entries.isEmpty() && entries.peekFirst().fireTime().isBefore(cutoff)) {
                entries.removeFirst();
            }
            while (entries.size() > maxEntries) {
                entries.removeFirst();
            }
        }

        synchronized List<DecisionTimelineEntry> snapshot() {
            return List.copyOf(entries);
        }
    }

}
