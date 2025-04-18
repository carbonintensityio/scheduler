package io.carbonintensity.scheduler.quarkus.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.quarkus.common.runtime.ImmutableScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.MutableScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.SchedulerContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class SchedulerRecorder {

    public Supplier<Object> createContext(List<MutableScheduledMethod> scheduledMethods) {
        // Defensive design - make an immutable copy of the scheduled method metadata
        List<ScheduledMethod> metadata = immutableCopy(scheduledMethods);
        return () -> new SchedulerContext() {

            @Override
            public List<ScheduledMethod> getScheduledMethods() {
                return metadata;
            }

            @Override
            public List<ScheduledMethod> getScheduledMethods(String implementation) {
                List<ScheduledMethod> ret = new ArrayList<>(metadata.size());
                for (ScheduledMethod method : metadata) {
                    for (GreenScheduled scheduled : method.getSchedules()) {
                        //if (matchesImplementation(scheduled, implementation)) {
                        ret.add(method);
                        //}
                    }
                }
                return ret;
            }

            @Override
            public String autoImplementation() {
                return "autoImplementation";
            }

        };
    }

    private List<ScheduledMethod> immutableCopy(List<MutableScheduledMethod> scheduledMethods) {
        List<ScheduledMethod> metadata = new ArrayList<>(scheduledMethods.size());
        for (ScheduledMethod scheduledMethod : scheduledMethods) {
            metadata.add(new ImmutableScheduledMethod(scheduledMethod.getInvokerClassName(),
                    scheduledMethod.getDeclaringClassName(), scheduledMethod.getMethodName(), scheduledMethod.getSchedules()));
        }
        return List.copyOf(metadata);
    }
}
