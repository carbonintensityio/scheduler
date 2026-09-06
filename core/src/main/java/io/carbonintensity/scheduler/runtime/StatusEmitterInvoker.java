package io.carbonintensity.scheduler.runtime;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.carbonintensity.scheduler.ScheduledExecution;
import io.carbonintensity.scheduler.Trigger;

/**
 * An invoker wrapper that fires events when an execution of a scheduled method is finished.
 * <p>
 * Also the wrapping point for MDC enrichment: every log line emitted during a job's invocation carries the job's
 * {@code identity}, {@code strategy} and {@code zone} (the same three canonical field names CIIO-282's metric tags
 * already use), without every log statement in the invoker chain needing to pass them explicitly.
 */
public final class StatusEmitterInvoker extends DelegateInvoker {

    private static final String MDC_IDENTITY_KEY = "identity";
    private static final String MDC_STRATEGY_KEY = "strategy";
    private static final String MDC_ZONE_KEY = "zone";

    private static final Logger log = LoggerFactory.getLogger(StatusEmitterInvoker.class);
    private final Events events;

    public StatusEmitterInvoker(ScheduledInvoker delegate, Events events) {
        super(delegate);
        this.events = events;
    }

    @Override
    public CompletionStage<Void> invoke(ScheduledExecution execution) {
        Trigger trigger = execution.getTrigger();
        putMdc(trigger);
        try {
            log.trace("Running status emitter invoker for {} at {}.", trigger.getId(), execution.getScheduledFireTime());
            return invokeDelegate(execution).whenComplete((v, t) -> {
                // whenComplete's callback may run on a different thread than this call (a genuinely async delegate)
                // - re-establish the MDC for that thread rather than assuming it inherited the one above, and
                // always remove it again afterward so a shared, pooled executor thread never leaks it into whatever
                // unrelated job runs on it next.
                putMdc(trigger);
                try {
                    if (t != null) {
                        log.error("Error occurred while executing task for trigger {}", trigger, t);
                        events.fireJobExecutionFailed(execution, t);
                    } else {
                        events.fireJobExecutionSuccessful(execution);
                    }
                } finally {
                    removeMdc();
                }
            });
        } finally {
            removeMdc();
        }
    }

    private static void putMdc(Trigger trigger) {
        MDC.put(MDC_IDENTITY_KEY, trigger.getId());
        trigger.getDecisionStrategy().ifPresent(strategy -> MDC.put(MDC_STRATEGY_KEY, strategy.name()));
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
