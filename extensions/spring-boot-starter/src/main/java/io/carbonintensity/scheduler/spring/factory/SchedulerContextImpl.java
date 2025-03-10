package io.carbonintensity.scheduler.spring.factory;

import java.util.List;

import io.carbonintensity.scheduler.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.runtime.SchedulerContext;

/**
 * Scheduler context. Can be removed if scheduler-core provides any implementation.
 */
public class SchedulerContextImpl implements SchedulerContext {

    private final List<ScheduledMethod> scheduledMethods;
    private final boolean forceSchedulerStart;

    /**
     * Constructs a SchedulerContextImpl
     *
     * @param scheduledMethods list of scheduled methods to schedule and run.
     * @param forceSchedulerStart See {@link SchedulerContext#forceSchedulerStart()}
     */
    public SchedulerContextImpl(List<ScheduledMethod> scheduledMethods,
            boolean forceSchedulerStart) {
        this.scheduledMethods = scheduledMethods;
        this.forceSchedulerStart = forceSchedulerStart;
    }

    @Override
    public List<ScheduledMethod> getScheduledMethods() {
        return scheduledMethods;
    }

    @Override
    public boolean forceSchedulerStart() {
        return forceSchedulerStart;
    }
}
