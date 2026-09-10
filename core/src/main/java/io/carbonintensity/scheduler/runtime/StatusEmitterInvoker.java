package io.carbonintensity.scheduler.runtime;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.scheduler.ScheduledExecution;
import io.carbonintensity.scheduler.Trigger;

/**
 * An invoker wrapper that fires events when an execution of a scheduled method is finished.
 */
public final class StatusEmitterInvoker extends DelegateInvoker {

    private static final Logger log = LoggerFactory.getLogger(StatusEmitterInvoker.class);
    private final Events events;

    public StatusEmitterInvoker(ScheduledInvoker delegate, Events events) {
        super(delegate);
        this.events = events;
    }

    @Override
    public CompletionStage<Void> invoke(ScheduledExecution execution) {
        Trigger trigger = execution.getTrigger();
        if (log.isTraceEnabled()) {
            log.trace("Running status emitter invoker for {} at {}.", trigger.getId(), execution.getScheduledFireTime());
        }
        return invokeDelegate(execution).whenComplete((v, t) -> {
            // re-establish MDC in case this runs on a different thread
            MdcEnrichingInvoker.putMdc(trigger);
            if (t != null) {
                log.error("Error occurred while executing task for trigger {}", trigger, t);
                events.fireJobExecutionFailed(execution, t);
            } else {
                events.fireJobExecutionSuccessful(execution);
            }
        });
    }

}
