package io.carbonintensity.scheduler.spring.factory;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import io.carbonintensity.scheduler.Scheduler;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;

/**
 * Spring {@link Scheduler} factory backed by {@link SchedulerFactory}.
 * Note: {@link Autowired} with constructor doesn't work, beans must
 * be injected lazy after constructor.
 */
public class SpringSchedulerFactory implements FactoryBean<Scheduler> {

    @Autowired //NOSONAR see javadoc above
    private SchedulerFactory schedulerFactory;

    @Autowired //NOSONAR see javadoc above
    private SchedulerConfig schedulerConfig;

    @Autowired(required = false) //NOSONAR see javadoc above
    private Scheduler.EventListener jobListener;

    @Override
    public Scheduler getObject() {
        var scheduler = schedulerFactory.createScheduler(schedulerConfig);
        if (jobListener != null) {
            scheduler.addJobListener(jobListener);
        }
        return scheduler;
    }

    @Override
    public Class<Scheduler> getObjectType() {
        return Scheduler.class;
    }

    public void setSchedulerConfig(SchedulerConfig schedulerConfig) {
        this.schedulerConfig = schedulerConfig;
    }

    public void setJobListener(Scheduler.EventListener jobListener) {
        this.jobListener = jobListener;
    }

    public void setSchedulerFactory(SchedulerFactory schedulerFactory) {
        this.schedulerFactory = schedulerFactory;
    }
}
