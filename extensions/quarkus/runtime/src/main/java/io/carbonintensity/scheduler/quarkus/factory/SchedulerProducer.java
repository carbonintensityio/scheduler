package io.carbonintensity.scheduler.quarkus.factory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensityDataFetcherImpl;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApiType;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityRestApi;
import io.carbonintensity.scheduler.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.carbonintensity.scheduler.runtime.impl.SchedulerContextImpl;
import io.carbonintensity.scheduler.runtime.impl.rest.CarbonIntensityFileApi;
import io.quarkus.arc.DefaultBean;

public class SchedulerProducer {
    private final Logger logger = LoggerFactory.getLogger(SchedulerProducer.class);

    @Inject
    GreenScheduledProperties greenScheduledProperties;

    @Produces
    @DefaultBean
    public SchedulerConfig schedulerConfig() {
        return new SchedulerConfigBuilder(greenScheduledProperties).build();
    }

    @Produces
    @Singleton
    SimpleScheduler createScheduler(SchedulerConfig schedulerConfig) {
        final var restApi = new CarbonIntensityRestApi(schedulerConfig.getCarbonIntensityApiConfig(),
                CarbonIntensityApiType.PREDICTED);
        final var fallbackApi = new CarbonIntensityFileApi();
        final var dataFetcher = new CarbonIntensityDataFetcherImpl(restApi, fallbackApi);
        var greenScheduledContext = createSchedulerContext(schedulerConfig, new ArrayList<>());
        logger.info("Creating green scheduler");
        return new SimpleScheduler(greenScheduledContext, schedulerConfig, dataFetcher, null, Clock.systemDefaultZone());
    }

    protected SchedulerContextImpl createSchedulerContext(SchedulerConfig schedulerConfig,
            List<ScheduledMethod> greenScheduledMethods) {
        var forceSchedulerStart = SchedulerConfig.StartMode.FORCED.equals(schedulerConfig.getStartMode());
        return new SchedulerContextImpl(greenScheduledMethods, forceSchedulerStart);
    }

}
