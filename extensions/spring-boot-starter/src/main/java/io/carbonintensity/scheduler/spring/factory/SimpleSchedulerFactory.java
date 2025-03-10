package io.carbonintensity.scheduler.spring.factory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensityDataFetcherImpl;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApiType;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityRestApi;
import io.carbonintensity.scheduler.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.carbonintensity.scheduler.runtime.impl.rest.CarbonIntensityFileApi;

/**
 * {@link SimpleScheduler} factory.
 */
public class SimpleSchedulerFactory implements SchedulerFactory {

    private final Logger logger = LoggerFactory.getLogger(SimpleSchedulerFactory.class);

    private final Clock clock;

    public SimpleSchedulerFactory(Clock clock) {
        this.clock = clock;
    }

    public SimpleScheduler createScheduler(SchedulerConfig schedulerConfig) {
        final var restApi = new CarbonIntensityRestApi(schedulerConfig.getCarbonIntensityApiConfig(),
                CarbonIntensityApiType.PREDICTED);
        final var fallbackApi = new CarbonIntensityFileApi();
        final var dataFetcher = new CarbonIntensityDataFetcherImpl(restApi, fallbackApi);
        var greenScheduledContext = createSchedulerContext(schedulerConfig, new ArrayList<>());
        logger.info("Creating scheduler");
        return new SimpleScheduler(greenScheduledContext, schedulerConfig, dataFetcher, null, clock);
    }

    protected SchedulerContextImpl createSchedulerContext(SchedulerConfig schedulerConfig,
            List<ScheduledMethod> greenScheduledMethods) {
        var forceSchedulerStart = SchedulerConfig.StartMode.FORCED.equals(schedulerConfig.getStartMode());
        return new SchedulerContextImpl(greenScheduledMethods, forceSchedulerStart);
    }

}
