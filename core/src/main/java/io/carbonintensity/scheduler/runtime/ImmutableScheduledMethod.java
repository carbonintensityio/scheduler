package io.carbonintensity.scheduler.runtime;

import java.util.List;
import java.util.Objects;

import io.carbonintensity.scheduler.GreenScheduled;

/**
 * Immutable representation of a scheduled method.
 * <p>
 * This class encapsulates the details of a scheduled method, including the method's invoker,
 * the name of the class that declares the method, the method name itself, and the associated
 * {@link GreenScheduled} annotations that define its scheduling details.
 */
public final class ImmutableScheduledMethod implements ScheduledMethod {

    private final ScheduledInvoker invoker;
    private final String declaringClassName;
    private final String methodName;
    private final List<GreenScheduled> schedules;

    public ImmutableScheduledMethod(ScheduledInvoker invoker, String declaringClassName, String methodName,
            List<GreenScheduled> schedules) {
        this.invoker = Objects.requireNonNull(invoker);
        this.declaringClassName = Objects.requireNonNull(declaringClassName);
        this.methodName = Objects.requireNonNull(methodName);
        this.schedules = List.copyOf(schedules);
    }

    public ScheduledInvoker getInvoker() {
        return invoker;
    }

    public String getDeclaringClassName() {
        return declaringClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<GreenScheduled> getSchedules() {
        return schedules;
    }

}
