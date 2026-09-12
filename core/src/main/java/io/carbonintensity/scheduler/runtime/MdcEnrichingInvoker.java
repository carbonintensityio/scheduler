package io.carbonintensity.scheduler.runtime;

import java.util.concurrent.CompletionStage;

import org.slf4j.MDC;

import io.carbonintensity.scheduler.ScheduledExecution;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.observability.DecisionStrategy;

/**
 * The outermost wrapper of every invoker chain (see
 * {@link SimpleScheduler#initInvoker}) - every log line in the chain during
 * a job's invocation carries the job's {@code identity}, {@code strategy}
 * and {@code zone} (the same three canonical names settled on for the
 * not-yet-implemented CIIO-282 metrics work), without every log statement
 * needing to pass them explicitly.
 */
final class MdcEnrichingInvoker extends DelegateInvoker {

    private static final String MDC_IDENTITY_KEY = "identity";
    private static final String MDC_STRATEGY_KEY = "strategy";
    private static final String MDC_ZONE_KEY = "zone";

    MdcEnrichingInvoker(ScheduledInvoker delegate) {
        super(delegate);
    }

    /**
     * MDC is thread-local, so it's cleared eagerly on the calling (dispatch)
     * thread as soon as this call returns, even if the returned
     * {@link CompletionStage} is still pending - that thread comes from a
     * shared, fixed-size job executor pool and may be handed the next job's
     * dispatch immediately, so it must never carry a stale value into an
     * unrelated job.
     */
    @Override
    public CompletionStage<Void> invoke(ScheduledExecution execution) {
        Trigger trigger = execution.getTrigger();
        putMdc(trigger);
        try {
            return invokeDelegate(execution);
        } finally {
            removeMdc();
        }
    }

    /**
     * A job's own async continuations that log after this method returns,
     * on a thread of their own choosing, are outside what this can
     * guarantee - propagating MDC across arbitrary executor boundaries is
     * a larger change than fits here.
     */
    static void putMdc(Trigger trigger) {
        MDC.put(MDC_IDENTITY_KEY, trigger.getId());
        DecisionStrategy strategy = trigger.getDecisionStrategy();
        if (strategy != null) {
            MDC.put(MDC_STRATEGY_KEY, strategy.name());
        }
        String zone = trigger.getCarbonIntensityZone();
        if (zone != null) {
            MDC.put(MDC_ZONE_KEY, zone);
        }
    }

    private static void removeMdc() {
        MDC.remove(MDC_IDENTITY_KEY);
        MDC.remove(MDC_STRATEGY_KEY);
        MDC.remove(MDC_ZONE_KEY);
    }
}
