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

    /**
     * Not {@code final}: this is a Quarkus {@link Recorder} class. At build time, Quarkus invokes
     * {@code public} recorder methods on a bytecode-recording proxy that needs to override them, so
     * a {@code final} method here makes the application fail to build with a
     * "cannot be proxied as it is final" error.
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
