package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;

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
        ExecutionWindow first = new ExecutionWindow(Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-01T10:00:05Z"));
        ExecutionWindow second = new ExecutionWindow(Instant.parse("2026-09-02T10:00:00Z"),
                Instant.parse("2026-09-02T10:00:05Z"));
        history.record("job-a", first.start(), first.end());
        history.record("job-a", second.start(), second.end());

        history.remove("job-a", List.of(first));

        assertThat(history.windowsFor("job-a")).containsExactly(second);
    }

    @Test
    void removingFromUnknownIdentityShouldBeANoOp() {
        CarbonImpactHistory history = new CarbonImpactHistory();

        history.remove("unknown", List.of(new ExecutionWindow(Instant.EPOCH, Instant.EPOCH)));

        assertThat(history.windowsFor("unknown")).isEmpty();
    }
}
