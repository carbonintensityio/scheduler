package io.carbonintensity.scheduler.quarkus.factory;

import java.util.Optional;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApi;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.quarkus.arc.DefaultBean;

public class SchedulerProducer {
    private final Logger logger = LoggerFactory.getLogger(SchedulerProducer.class);

    @Inject
    GreenScheduledProperties greenScheduledProperties;

    @Inject
    Optional<CarbonIntensityApi> carbonIntensityApi;

    @Produces
    @DefaultBean
    public SchedulerConfig schedulerConfig() {
        var schedulerConfig = new SchedulerConfigBuilder(greenScheduledProperties);
        carbonIntensityApi.ifPresent(schedulerConfig::carbonIntensityApi);
        return schedulerConfig.build();
    }

    @Produces
    @Singleton
    SimpleScheduler createScheduler(SchedulerConfig schedulerConfig) {
        logger.info("Creating green scheduler");
        return new SimpleScheduler(schedulerConfig);
    }

}
