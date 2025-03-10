package io.carbonintensity.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.carbonintensity.scheduler.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.runtime.SchedulerContext;

public class TestSchedulerContext implements SchedulerContext {

    private final List<ScheduledMethod> scheduledMethods;

    private final boolean forceSchedulerStart;

    private TestSchedulerContext(List<ScheduledMethod> scheduledMethods, boolean forceSchedulerStart) {
        this.scheduledMethods = scheduledMethods;
        this.forceSchedulerStart = forceSchedulerStart;
    }

    @Override
    public List<ScheduledMethod> getScheduledMethods() {
        return this.scheduledMethods;
    }

    @Override
    public boolean forceSchedulerStart() {
        return this.forceSchedulerStart;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private final List<ScheduledMethod> scheduledMethods;
        private boolean forceScheduleStart;

        private Builder() {
            this.scheduledMethods = new ArrayList<>();
            this.forceScheduleStart = false;
        }

        public Builder withScheduledMethod(ScheduledMethod... methods) {
            scheduledMethods.addAll(Arrays.asList(methods));
            return this;
        }

        public Builder withScheduledMethod(Iterable<ScheduledMethod> methods) {
            methods.forEach(scheduledMethods::add);
            return this;
        }

        public Builder withForceScheduleStart(boolean force) {
            this.forceScheduleStart = force;
            return this;
        }

        public TestSchedulerContext build() {
            return new TestSchedulerContext(scheduledMethods, forceScheduleStart);
        }
    }
}
