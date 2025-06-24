package io.carbonintensity.scheduler.spring.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.scheduler.runtime.SchedulerConfig;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;

/**
 * {@link SimpleScheduler} factory.
 */
public class SimpleSchedulerFactory implements SchedulerFactory {

    private final Logger logger = LoggerFactory.getLogger(SimpleSchedulerFactory.class);

    public SimpleScheduler createScheduler(SchedulerConfig schedulerConfig) {
        logger.info("Creating scheduler");
        return new SimpleScheduler(schedulerConfig);
    }

}
