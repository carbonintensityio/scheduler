package io.carbonintensity.scheduler.runtime;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory record of successful executions for {@code carbonImpact}-enabled jobs, awaiting processing by the
 * carbon-impact batch.
 * <p>
 * Deliberately not persisted: if the application restarts before the batch runs, that day's executions are lost for
 * good, and never contribute to the job's carbon-impact/savings figures - see {@code CarbonImpactBatchTrigger}. The
 * cumulative savings total is therefore a lower bound, not a reconciled ledger.
 */
final class CarbonImpactHistory {

    private final ConcurrentMap<String, List<ExecutionWindow>> windowsByIdentity = new ConcurrentHashMap<>();

    void record(String identity, Instant start, Instant end) {
        windowsByIdentity.computeIfAbsent(identity, id -> new CopyOnWriteArrayList<>())
                .add(new ExecutionWindow(start, end));
    }

    List<ExecutionWindow> windowsFor(String identity) {
        return windowsByIdentity.getOrDefault(identity, List.of());
    }

    /**
     * Removes windows that were successfully processed by the batch, so they are not counted again on a later run.
     */
    void remove(String identity, List<ExecutionWindow> processed) {
        List<ExecutionWindow> windows = windowsByIdentity.get(identity);
        if (windows != null) {
            windows.removeAll(processed);
        }
    }

}
