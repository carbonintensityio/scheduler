package io.carbonintensity.scheduler.runtime;

import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import io.carbonintensity.scheduler.observability.CarbonImpactHistoryStore;
import io.carbonintensity.scheduler.observability.ExecutionWindow;

/**
 * The default {@link CarbonImpactHistoryStore}: an in-memory record of successful executions for
 * {@code carbonImpact}-enabled jobs, awaiting processing by the carbon-impact batch.
 * <p>
 * Deliberately not persisted: if the application restarts before the batch runs, that day's executions are lost for
 * good, and never contribute to the job's carbon-impact/savings figures - see {@code CarbonImpactBatchTrigger}. The
 * cumulative savings total is therefore a lower bound, not a reconciled ledger. A consumer needing that data to
 * survive a restart can implement {@link CarbonImpactHistoryStore} against their own datastore instead - see
 * {@link SchedulerConfig#setCarbonImpactHistoryStore}.
 */
public final class InMemoryCarbonImpactHistoryStore implements CarbonImpactHistoryStore {

    private final ConcurrentMap<String, ConcurrentLinkedQueue<ExecutionWindow>> windowsByIdentity = new ConcurrentHashMap<>();

    @Override
    public void record(String identity, Instant start, Instant end) {
        windowsByIdentity.computeIfAbsent(identity, id -> new ConcurrentLinkedQueue<>())
                .add(new ExecutionWindow(start, end));
    }

    @Override
    public List<ExecutionWindow> windowsFor(String identity) {
        ConcurrentLinkedQueue<ExecutionWindow> windows = windowsByIdentity.get(identity);
        return windows == null ? List.of() : List.copyOf(windows);
    }

    @Override
    public void remove(String identity, List<ExecutionWindow> processed) {
        ConcurrentLinkedQueue<ExecutionWindow> windows = windowsByIdentity.get(identity);
        if (windows == null || processed.isEmpty()) {
            return;
        }
        Set<ExecutionWindow> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        toRemove.addAll(processed);
        windows.removeIf(toRemove::contains);
    }

}
