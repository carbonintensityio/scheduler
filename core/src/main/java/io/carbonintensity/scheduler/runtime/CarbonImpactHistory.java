package io.carbonintensity.scheduler.runtime;

import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory record of successful executions for {@code carbonImpact}-enabled jobs, awaiting processing by the
 * carbon-impact batch.
 * <p>
 * Deliberately not persisted: if the application restarts before the batch runs, that day's executions are lost for
 * good, and never contribute to the job's carbon-impact/savings figures - see {@code CarbonImpactBatchTrigger}. The
 * cumulative savings total is therefore a lower bound, not a reconciled ledger.
 */
final class CarbonImpactHistory {

    private final ConcurrentMap<String, ConcurrentLinkedQueue<ExecutionWindow>> windowsByIdentity = new ConcurrentHashMap<>();

    void record(String identity, Instant start, Instant end) {
        windowsByIdentity.computeIfAbsent(identity, id -> new ConcurrentLinkedQueue<>())
                .add(new ExecutionWindow(start, end));
    }

    /**
     * @return an immutable snapshot of the windows currently recorded for {@code identity}
     */
    List<ExecutionWindow> windowsFor(String identity) {
        ConcurrentLinkedQueue<ExecutionWindow> windows = windowsByIdentity.get(identity);
        return windows == null ? List.of() : List.copyOf(windows);
    }

    /**
     * Removes windows that were successfully processed by the batch, so they are not counted again on a later run.
     * <p>
     * Matches by reference, not {@link ExecutionWindow#equals(Object)}: two distinct executions can otherwise share
     * an identical (start, end), and value-equality removal would then delete an unprocessed window too.
     */
    void remove(String identity, List<ExecutionWindow> processed) {
        ConcurrentLinkedQueue<ExecutionWindow> windows = windowsByIdentity.get(identity);
        if (windows == null || processed.isEmpty()) {
            return;
        }
        Set<ExecutionWindow> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        toRemove.addAll(processed);
        windows.removeIf(toRemove::contains);
    }

}
