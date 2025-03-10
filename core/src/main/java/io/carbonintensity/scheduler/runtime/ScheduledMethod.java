package io.carbonintensity.scheduler.runtime;

import java.util.List;

import io.carbonintensity.scheduler.GreenScheduled;

/**
 * Scheduled method metadata.
 *
 */
public interface ScheduledMethod {
    ScheduledInvoker getInvoker();

    String getDeclaringClassName();

    String getMethodName();

    List<GreenScheduled> getSchedules();

    default String getMethodDescription() {
        return getDeclaringClassName() + "#" + getMethodName();
    }

}