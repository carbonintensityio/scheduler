package io.carbonintensity.scheduler.runtime;

import java.util.concurrent.CompletionStage;

import io.carbonintensity.scheduler.ScheduledExecution;

/**
 * Interface for invoking the scheduledTask
 *
 * @see io.carbonintensity.scheduler.runtime.SimpleScheduler.ScheduledTask
 */
public interface ScheduledInvoker {

    /**
     * @param execution
     * @return the result
     * @throws Exception
     */
    CompletionStage<Void> invoke(ScheduledExecution execution) throws Exception;
}
