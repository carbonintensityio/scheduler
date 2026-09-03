package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class CarbonImpactHistoryTest {

    @Test
    void shouldReturnEmptyListForUnknownIdentity() {
        CarbonImpactHistory history = new CarbonImpactHistory();

        assertThat(history.windowsFor("unknown")).isEmpty();
    }

    @Test
    void shouldRecordAndReturnWindowsPerIdentity() {
        CarbonImpactHistory history = new CarbonImpactHistory();
        Instant start = Instant.parse("2026-09-02T10:00:00Z");
        Instant end = Instant.parse("2026-09-02T10:00:05Z");

        history.record("job-a", start, end);

        assertThat(history.windowsFor("job-a")).containsExactly(new ExecutionWindow(start, end));
        assertThat(history.windowsFor("job-b")).isEmpty();
    }

    @Test
    void shouldRemoveOnlyProcessedWindows() {
        CarbonImpactHistory history = new CarbonImpactHistory();
        history.record("job-a", Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T10:00:05Z"));
        history.record("job-a", Instant.parse("2026-09-02T10:00:00Z"), Instant.parse("2026-09-02T10:00:05Z"));

        // the batch's real workflow: fetch the actual stored windows, process them, then hand the same
        // instances back to remove() - see the identity-based-removal test below for why that matters.
        List<ExecutionWindow> windows = history.windowsFor("job-a");
        ExecutionWindow first = windows.get(0);
        ExecutionWindow second = windows.get(1);

        history.remove("job-a", List.of(first));

        assertThat(history.windowsFor("job-a")).containsExactly(second);
    }

    @Test
    void removeShouldMatchByReferenceNotByValueEquality() {
        // two distinct executions can share an identical (start, end) - e.g. with a coarse test Clock. remove()
        // must not delete both just because a caller passes in a value-equal, but not the same, instance.
        CarbonImpactHistory history = new CarbonImpactHistory();
        Instant start = Instant.parse("2026-09-01T10:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:05Z");
        history.record("job-a", start, end);
        history.record("job-a", start, end);

        ExecutionWindow lookAlike = new ExecutionWindow(start, end);
        history.remove("job-a", List.of(lookAlike));

        assertThat(history.windowsFor("job-a")).hasSize(2);
    }

    @Test
    void removingFromUnknownIdentityShouldBeANoOp() {
        CarbonImpactHistory history = new CarbonImpactHistory();

        history.remove("unknown", List.of(new ExecutionWindow(Instant.EPOCH, Instant.EPOCH)));

        assertThat(history.windowsFor("unknown")).isEmpty();
    }

    @Test
    void windowsForShouldReturnAnImmutableSnapshot() {
        CarbonImpactHistory history = new CarbonImpactHistory();
        history.record("job-a", Instant.EPOCH, Instant.EPOCH);

        List<ExecutionWindow> windows = history.windowsFor("job-a");

        assertThat(windows).hasSize(1);
        assertThatThrownBy(() -> windows.add(new ExecutionWindow(Instant.EPOCH, Instant.EPOCH)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
