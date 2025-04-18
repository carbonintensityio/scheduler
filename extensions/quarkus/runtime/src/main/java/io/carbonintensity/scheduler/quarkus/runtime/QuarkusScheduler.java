package io.carbonintensity.scheduler.quarkus.runtime;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;

import org.jboss.logging.Logger;

import io.carbonintensity.scheduler.quarkus.common.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.SchedulerContext;
import io.carbonintensity.scheduler.runtime.ImmutableScheduledMethod;
import io.carbonintensity.scheduler.runtime.ScheduledInvoker;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.quarkus.runtime.StartupEvent;

@Singleton
public class QuarkusScheduler {

    private static final Logger LOG = Logger.getLogger(QuarkusScheduler.class);

    public QuarkusScheduler(SchedulerContext context, SimpleScheduler greenScheduler) {
        LOG.info("======================================= sched methods = "
                + context.getScheduledMethods().size());
        if (context.getScheduledMethods().isEmpty()) {
            LOG.info("No scheduled business methods found");
            return;
        }

        // Create triggers and invokers for @GreenScheduled methods
        for (ScheduledMethod method : context.getScheduledMethods()) {
            LOG.info("^^^^^^^1 sched method =" + method.getMethodDescription());

            ScheduledInvoker invoker = context.createInvoker(method.getInvokerClassName());
            greenScheduler.scheduleMethod(new ImmutableScheduledMethod(invoker, method.getDeclaringClassName(),
                    method.getMethodName(), method.getSchedules()));
        }
    }

    // Use Interceptor.Priority.PLATFORM_BEFORE to start the scheduler before regular StartupEvent observers
    void start(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent event) {
        LOG.info("!!START");
    }

    @PreDestroy
    void stop() {
        LOG.info("!!STOP");
    }

}
