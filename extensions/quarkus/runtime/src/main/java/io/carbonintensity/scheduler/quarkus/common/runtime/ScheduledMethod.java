package io.carbonintensity.scheduler.quarkus.common.runtime;

import java.util.List;

import io.carbonintensity.scheduler.GreenScheduled;

/**
 * Scheduled method metadata.
 */
public interface ScheduledMethod {

    String getInvokerClassName();

    String getDeclaringClassName();

    String getMethodName();

    List<GreenScheduled> getSchedules();

    default String getMethodDescription() {
        return getDeclaringClassName() + "#" + getMethodName();
    }

}
