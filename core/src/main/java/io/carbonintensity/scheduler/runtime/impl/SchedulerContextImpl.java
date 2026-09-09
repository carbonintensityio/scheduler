package io.carbonintensity.scheduler.runtime.impl;

import java.util.List;

import io.carbonintensity.scheduler.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.runtime.SchedulerContext;

/**
 * Scheduler context
 */
public class SchedulerContextImpl implements SchedulerContext {

    private final List<ScheduledMethod> scheduledMethods;

    /**
     * Constructs a SchedulerContextImpl
     *
     * @param scheduledMethods list of scheduled methods to schedule and run.
     */
    public SchedulerContextImpl(List<ScheduledMethod> scheduledMethods) {
        this.scheduledMethods = scheduledMethods;
    }

    @Override
    public List<ScheduledMethod> getScheduledMethods() {
        return scheduledMethods;
    }

}
