package io.carbonintensity.scheduler.runtime;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.carbonintensity.scheduler.ScheduledExecution;

/**
 * An invoker wrapper that fires events when an execution of a scheduled method is finished.
 * <p>
 * Also the wrapping point for MDC enrichment: every log line emitted during a job's invocation carries the job's
 * {@code identity} (the canonical field name, matching what the metrics/decision-timeline machinery already uses),
 * without every log statement in the invoker chain needing to pass it explicitly.
 */
public final class StatusEmitterInvoker extends DelegateInvoker {

    private static final String MDC_IDENTITY_KEY = "identity";

    private static final Logger log = LoggerFactory.getLogger(StatusEmitterInvoker.class);
    private final Events events;

    public StatusEmitterInvoker(ScheduledInvoker delegate, Events events) {
        super(delegate);
        this.events = events;
    }

    @Override
    public CompletionStage<Void> invoke(ScheduledExecution execution) {
        String identity = execution.getTrigger().getId();
        MDC.put(MDC_IDENTITY_KEY, identity);
        try {
            log.trace("Running status emitter invoker for {} at {}.", identity, execution.getScheduledFireTime());
            return invokeDelegate(execution).whenComplete((v, t) -> {
                // whenComplete's callback may run on a different thread than this call (a genuinely async delegate)
                // - re-establish the identity for that thread rather than assuming it inherited the one above, and
                // always remove it again afterward so a shared, pooled executor thread never leaks it into whatever
                // unrelated job runs on it next.
                MDC.put(MDC_IDENTITY_KEY, identity);
                try {
                    if (t != null) {
                        log.error("Error occurred while executing task for trigger {}", execution.getTrigger(), t);
                        events.fireJobExecutionFailed(execution, t);
                    } else {
                        events.fireJobExecutionSuccessful(execution);
                    }
                } finally {
                    MDC.remove(MDC_IDENTITY_KEY);
                }
            });
        } finally {
            MDC.remove(MDC_IDENTITY_KEY);
        }
    }

}
