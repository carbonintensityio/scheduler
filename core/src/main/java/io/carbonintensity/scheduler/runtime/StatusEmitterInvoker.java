package io.carbonintensity.scheduler.runtime;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.scheduler.ScheduledExecution;

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
        log.trace("Running status emitter invoker for {} at {}.", execution.getTrigger().getId(),
                execution.getScheduledFireTime());
        return invokeDelegate(execution).whenComplete((v, t) -> {
            if (t != null) {
                log.error("Error occurred while executing task for trigger {}", execution.getTrigger(), t);
                events.fireJobExecutionFailed(execution, t);
            } else {
                events.fireJobExecutionSuccessful(execution);
            }
        });
    }

}
