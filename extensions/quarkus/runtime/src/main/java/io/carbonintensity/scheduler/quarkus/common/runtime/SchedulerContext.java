package io.carbonintensity.scheduler.quarkus.common.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import io.carbonintensity.scheduler.runtime.ScheduledInvoker;

public interface SchedulerContext {

    List<ScheduledMethod> getScheduledMethods();

    @SuppressWarnings("unchecked")
    default ScheduledInvoker createInvoker(String invokerClassName) {
        try {
            Class<? extends ScheduledInvoker> invokerClazz = (Class<? extends ScheduledInvoker>) Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass(invokerClassName);
            return invokerClazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException
                | InvocationTargetException e) {
            throw new IllegalStateException("Unable to create invoker: " + invokerClassName, e);
        }
    }
}
