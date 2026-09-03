package io.carbonintensity.scheduler.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

import io.carbonintensity.scheduler.ScheduledExecution;

/**
 * An invoker wrapper that records the wall-clock start/end of every successful execution into a
 * {@link CarbonImpactHistory}, for a job whose {@code carbonImpact} observability is enabled.
 * <p>
 * Wraps the raw, innermost invoker - before {@link StatusEmitterInvoker} and the other decorators applied by
 * {@link SimpleScheduler#initInvoker} - so the recorded window reflects only the job's own execution time, not
 * scheduler overhead (skip-predicate checks, concurrency bookkeeping, ...). A failed execution is not recorded: it
 * did not produce the completed work the carbon-impact figure is meant to represent.
 */
final class CarbonImpactHistoryInvoker extends DelegateInvoker {

    private final Clock clock;
    private final String identity;
    private final CarbonImpactHistory history;

    CarbonImpactHistoryInvoker(ScheduledInvoker delegate, Clock clock, String identity, CarbonImpactHistory history) {
        super(delegate);
        this.clock = clock;
        this.identity = identity;
        this.history = history;
    }

    @Override
    public CompletionStage<Void> invoke(ScheduledExecution execution) {
        Instant start = clock.instant();
        return invokeDelegate(execution).whenComplete((result, throwable) -> {
            if (throwable == null) {
                history.record(identity, start, clock.instant());
            }
        });
    }

}
