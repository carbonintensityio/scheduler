package io.carbonintensity.scheduler.runtime;

import java.util.List;
import java.util.Optional;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.observability.GreenObserved;

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

    /**
     * The {@link GreenObserved} annotation present on this method, if any. A single {@link GreenObserved} applies
     * uniformly to every schedule returned by {@link #getSchedules()}.
     *
     * @return the {@link GreenObserved} configuration, or empty if the method opted out of observability data
     */
    default Optional<GreenObserved> getGreenObserved() {
        return Optional.empty();
    }

}