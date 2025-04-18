package io.carbonintensity.scheduler.quarkus.factory;

import java.time.Clock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApiConfig;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;

@ApplicationScoped
public class SchedulerProducer {

    @Produces
    @ApplicationScoped
    SimpleScheduler createScheduler() {
        var greenConfig = new io.carbonintensity.scheduler.runtime.SchedulerConfig();
        greenConfig.setEnabled(true);
        greenConfig.setCarbonIntensityApiConfig(new CarbonIntensityApiConfig.Builder().build());
        return new SimpleSchedulerFactory(Clock.systemDefaultZone()).createScheduler(greenConfig);

    }
}
