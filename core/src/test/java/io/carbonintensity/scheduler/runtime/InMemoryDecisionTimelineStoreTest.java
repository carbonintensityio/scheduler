package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.observability.DecisionReason;
import io.carbonintensity.scheduler.observability.DecisionStrategy;
import io.carbonintensity.scheduler.observability.DecisionTimelineEntry;

class InMemoryDecisionTimelineStoreTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void entriesForUnknownIdentityIsEmpty() {
        InMemoryDecisionTimelineStore store = newStore(BASE, Duration.ofDays(30), 100);

        assertThat(store.entriesFor("unknown")).isEmpty();
    }

    @Test
    void recordsAndReturnsEntriesOldestFirst() {
        InMemoryDecisionTimelineStore store = newStore(BASE, Duration.ofDays(30), 100);

        store.record("job-a", entryAt(BASE));
        store.record("job-a", entryAt(BASE.plusSeconds(60)));

        List<DecisionTimelineEntry> entries = store.entriesFor("job-a");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).fireTime()).isEqualTo(BASE);
        assertThat(entries.get(1).fireTime()).isEqualTo(BASE.plusSeconds(60));
    }

    @Test
    void keepsDifferentJobsIsolated() {
        InMemoryDecisionTimelineStore store = newStore(BASE, Duration.ofDays(30), 100);

        store.record("job-a", entryAt(BASE));
        store.record("job-b", entryAt(BASE));
        store.record("job-b", entryAt(BASE.plusSeconds(1)));

        assertThat(store.entriesFor("job-a")).hasSize(1);
        assertThat(store.entriesFor("job-b")).hasSize(2);
    }

    @Test
    void evictsEntriesOlderThanTheRetentionWindow() {
        // clock fixed at day 40 - entries older than 30 days (i.e. before day 10) should be pruned
        Instant now = BASE.plus(Duration.ofDays(40));
        InMemoryDecisionTimelineStore store = newStore(now, Duration.ofDays(30), 100);

        store.record("job-a", entryAt(BASE.plus(Duration.ofDays(5)))); // too old, evicted
        store.record("job-a", entryAt(BASE.plus(Duration.ofDays(15)))); // within window, kept
        store.record("job-a", entryAt(BASE.plus(Duration.ofDays(35)))); // within window, kept

        List<DecisionTimelineEntry> entries = store.entriesFor("job-a");
        assertThat(entries).extracting(DecisionTimelineEntry::fireTime)
                .containsExactly(BASE.plus(Duration.ofDays(15)), BASE.plus(Duration.ofDays(35)));
    }

    @Test
    void evictsOldestWhenTheEntryCountCapIsExceeded() {
        InMemoryDecisionTimelineStore store = newStore(BASE, Duration.ofDays(30), 3);

        for (int i = 0; i < 5; i++) {
            store.record("job-a", entryAt(BASE.plusSeconds(i)));
        }

        List<DecisionTimelineEntry> entries = store.entriesFor("job-a");
        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(DecisionTimelineEntry::fireTime)
                .containsExactly(BASE.plusSeconds(2), BASE.plusSeconds(3), BASE.plusSeconds(4));
    }

    @Test
    void bothCapsApplyTogetherWhicheverIsHitFirst() {
        // a 2-day retention window, but a count cap of 1 - the count cap bites first here
        InMemoryDecisionTimelineStore store = newStore(BASE, Duration.ofDays(2), 1);

        store.record("job-a", entryAt(BASE));
        store.record("job-a", entryAt(BASE.plusSeconds(1)));

        assertThat(store.entriesFor("job-a")).extracting(DecisionTimelineEntry::fireTime)
                .containsExactly(BASE.plusSeconds(1));
    }

    @Test
    void staysCorrectWhenEntriesArriveOutOfOrder() {
        // record() carries no ordering precondition - an out-of-order arrival must not fool age-based eviction
        // (which only ever inspects the front of the list) into keeping a genuinely-too-old entry buried
        // elsewhere, or evicting a young one instead
        Instant now = BASE.plus(Duration.ofDays(40));
        InMemoryDecisionTimelineStore store = newStore(now, Duration.ofDays(30), 100);

        store.record("job-a", entryAt(BASE.plus(Duration.ofDays(35)))); // within window, kept - arrives first
        store.record("job-a", entryAt(BASE.plus(Duration.ofDays(5)))); // too old, evicted - arrives out of order
        store.record("job-a", entryAt(BASE.plus(Duration.ofDays(15)))); // within window, kept

        assertThat(store.entriesFor("job-a")).extracting(DecisionTimelineEntry::fireTime)
                .containsExactly(BASE.plus(Duration.ofDays(15)), BASE.plus(Duration.ofDays(35)));
    }

    @Test
    void rejectsANonPositiveMaxEntriesPerJob() {
        assertThatThrownBy(() -> newStore(BASE, Duration.ofDays(30), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static InMemoryDecisionTimelineStore newStore(Instant now, Duration retention, int maxEntriesPerJob) {
        return new InMemoryDecisionTimelineStore(Clock.fixed(now, ZoneOffset.UTC), retention, maxEntriesPerJob);
    }

    private static DecisionTimelineEntry entryAt(Instant fireTime) {
        return new DecisionTimelineEntry(fireTime, DecisionStrategy.FIXED_WINDOW, DecisionReason.GREENEST_AVAILABLE_SLOT,
                OptionalDouble.empty());
    }
}
