package io.carbonintensity.scheduler.quarkus.common.runtime;

import java.util.List;

import io.carbonintensity.scheduler.GreenScheduled;

// This class is mutable so that it can be serialized in a recorder method
public class MutableScheduledMethod implements ScheduledMethod {

    private String invokerClassName;
    private String declaringClassName;
    private String methodName;
    private List<GreenScheduled> schedules;

    public final String getInvokerClassName() {
        return invokerClassName;
    }

    public final void setInvokerClassName(String invokerClassName) {
        this.invokerClassName = invokerClassName;
    }

    public final String getDeclaringClassName() {
        return declaringClassName;
    }

    public final void setDeclaringClassName(String declaringClassName) {
        this.declaringClassName = declaringClassName;
    }

    public final String getMethodName() {
        return methodName;
    }

    public final void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public final List<GreenScheduled> getSchedules() {
        return schedules;
    }

    public final void setSchedules(List<GreenScheduled> schedules) {
        this.schedules = schedules;
    }

}
