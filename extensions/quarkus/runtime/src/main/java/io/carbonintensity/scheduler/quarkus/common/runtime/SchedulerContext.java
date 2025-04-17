package io.carbonintensity.scheduler.quarkus.common.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.cronutils.model.CronType;
import io.carbonintensity.scheduler.runtime.ScheduledInvoker;


public interface SchedulerContext {

    CronType getCronType();

    List<ScheduledMethod> getScheduledMethods();

    boolean forceSchedulerStart();

    List<ScheduledMethod> getScheduledMethods(String implementation);

    String autoImplementation();

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
