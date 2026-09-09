package io.carbonintensity.scheduler.quarkus.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.carbonintensity.scheduler.quarkus.common.runtime.ImmutableScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.MutableScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.SchedulerContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class SchedulerRecorder {

    /*
     * Not final: Quarkus recorder methods run behind a bytecode-recording
     * proxy that must override them; final fails the build with
     * "cannot be proxied as it is final".
     */
    public Supplier<Object> createContext(List<MutableScheduledMethod> scheduledMethods) {
        // Defensive design - make an immutable copy of the scheduled method metadata
        List<ScheduledMethod> metadata = immutableCopy(scheduledMethods);
        return () -> (SchedulerContext) () -> metadata;
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
