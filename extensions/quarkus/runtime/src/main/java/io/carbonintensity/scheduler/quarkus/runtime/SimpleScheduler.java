package io.carbonintensity.scheduler.quarkus.runtime;

import java.time.Clock;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;

import org.jboss.logging.Logger;

import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApiConfig;
import io.carbonintensity.scheduler.quarkus.common.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.SchedulerContext;
import io.carbonintensity.scheduler.quarkus.factory.SimpleSchedulerFactory;
import io.carbonintensity.scheduler.runtime.ImmutableScheduledMethod;
import io.quarkus.runtime.StartupEvent;

@Singleton
public class SimpleScheduler {

    private static final Logger LOG = Logger.getLogger(SimpleScheduler.class);

    public SimpleScheduler(SchedulerContext context) {

        var greenConfig = new io.carbonintensity.scheduler.runtime.SchedulerConfig();
        greenConfig.setEnabled(true);
        greenConfig.setStartMode(io.carbonintensity.scheduler.runtime.SchedulerConfig.StartMode.FORCED);
        greenConfig.setCarbonIntensityApiConfig(new CarbonIntensityApiConfig.Builder().build());
        var greenScheduler = new SimpleSchedulerFactory(Clock.systemDefaultZone()).createScheduler(greenConfig);
        greenScheduler.start();

        LOG.info("======================================= sched methods = "
                + context.getScheduledMethods().size());
        if (context.getScheduledMethods().isEmpty()) {
            LOG.info("No scheduled business methods found - Simple scheduler will not be started");
            return;
        }

        // Create triggers and invokers for @Scheduled methods
        for (ScheduledMethod method : context.getScheduledMethods()) {
            LOG.info("!!#@32210=$-=@@ sched method =" + method.getMethodDescription());

            io.carbonintensity.scheduler.runtime.ScheduledInvoker invoker = context
                    .createInvoker(method.getInvokerClassName());
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
