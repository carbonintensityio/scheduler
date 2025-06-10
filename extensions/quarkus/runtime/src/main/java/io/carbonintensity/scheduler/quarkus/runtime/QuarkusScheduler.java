package io.carbonintensity.scheduler.quarkus.runtime;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.carbonintensity.scheduler.quarkus.common.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.SchedulerContext;
import io.carbonintensity.scheduler.runtime.ImmutableScheduledMethod;
import io.carbonintensity.scheduler.runtime.ScheduledInvoker;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.quarkus.runtime.Startup;

@Singleton
@Startup
public class QuarkusScheduler {

    private static final Logger LOG = Logger.getLogger(QuarkusScheduler.class);

    SimpleScheduler greenScheduler;

    public QuarkusScheduler(SchedulerContext context, SimpleScheduler greenScheduler) {
        this.greenScheduler = greenScheduler;
        if (context.getScheduledMethods().isEmpty()) {
            LOG.info("No scheduled business methods found");
            return;
        }

        // Create triggers and invokers for @GreenScheduled methods
        for (ScheduledMethod method : context.getScheduledMethods()) {
            ScheduledInvoker invoker = context.createInvoker(method.getInvokerClassName());
            greenScheduler.scheduleMethod(new ImmutableScheduledMethod(invoker, method.getDeclaringClassName(),
                    method.getMethodName(), method.getSchedules()));
        }
    }

    @PreDestroy
    void stop() {
        LOG.info("Stopping Green Scheduler");
        greenScheduler.stop();
    }

}
