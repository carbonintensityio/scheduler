package io.carbonintensity.scheduler.quarkus.common.runtime;

import java.util.List;

import io.carbonintensity.scheduler.GreenScheduled;

// This class is mutable so that it can be serialized in a recorder method
public class MutableScheduledMethod implements ScheduledMethod {

    private String invokerClassName;
    private String declaringClassName;
    private String methodName;
    private List<GreenScheduled> schedules;

    public String getInvokerClassName() {
        return invokerClassName;
    }

    public void setInvokerClassName(String invokerClassName) {
        this.invokerClassName = invokerClassName;
    }

    public String getDeclaringClassName() {
        return declaringClassName;
    }

    public void setDeclaringClassName(String declaringClassName) {
        this.declaringClassName = declaringClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<GreenScheduled> getSchedules() {
        return schedules;
    }

    public void setSchedules(List<GreenScheduled> schedules) {
        this.schedules = schedules;
    }

}
