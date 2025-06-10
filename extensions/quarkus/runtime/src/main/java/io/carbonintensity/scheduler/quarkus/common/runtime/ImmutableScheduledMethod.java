package io.carbonintensity.scheduler.quarkus.common.runtime;

import java.util.List;
import java.util.Objects;

import io.carbonintensity.scheduler.GreenScheduled;

public final class ImmutableScheduledMethod implements ScheduledMethod {

    private final String invokerClassName;
    private final String declaringClassName;
    private final String methodName;
    private final List<GreenScheduled> schedules;

    public ImmutableScheduledMethod(String invokerClassName, String declaringClassName, String methodName,
            List<GreenScheduled> schedules) {
        this.invokerClassName = Objects.requireNonNull(invokerClassName);
        this.declaringClassName = Objects.requireNonNull(declaringClassName);
        this.methodName = Objects.requireNonNull(methodName);
        this.schedules = List.copyOf(schedules);
    }

    public String getInvokerClassName() {
        return invokerClassName;
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
