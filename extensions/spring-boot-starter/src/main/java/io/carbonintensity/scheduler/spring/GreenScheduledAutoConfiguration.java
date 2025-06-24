package io.carbonintensity.scheduler.spring;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApi;
import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.carbonintensity.scheduler.spring.factory.ScheduledMethodFactory;
import io.carbonintensity.scheduler.spring.factory.SchedulerConfigBuilder;
import io.carbonintensity.scheduler.spring.factory.SchedulerFactory;
import io.carbonintensity.scheduler.spring.factory.SimpleSchedulerFactory;
import io.carbonintensity.scheduler.spring.factory.SpringSchedulerFactory;

/**
 * Green Scheduler Spring {@link AutoConfiguration}.
 *
 * <p>
 * This scheduler is by default, enabled and scans all spring managed beans for {@link GreenScheduled} annotations. Scheduler
 * can be configured using spring java configuration exposing {@link SchedulerConfig} as bean or by properties class or property
 * values. See {@link GreenScheduledProperties}.
 *
 * <p>
 * This scheduler starts only if you have jobs defined with annotation {@link GreenScheduled}.
 */
@Configuration
@ConditionalOnProperty(matchIfMissing = true, prefix = "greenscheduled", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({ GreenScheduledProperties.class })
public class GreenScheduledAutoConfiguration {

    private final Logger logger = LoggerFactory.getLogger(GreenScheduledAutoConfiguration.class);
    private SimpleScheduler simpleScheduler;

    @Bean
    @ConditionalOnMissingBean
    static GreenScheduledBeanProcessor createGreenScheduledBeanProcessor() {
        return new GreenScheduledBeanProcessor();
    }

    @Autowired
    private GreenScheduledProperties greenScheduledProperties;

    @Autowired(required = false)
    private CarbonIntensityApi carbonIntensityApi;

    @Bean
    @ConditionalOnMissingBean
    public SchedulerConfig schedulerConfig() {
        var configBuilder = new SchedulerConfigBuilder(greenScheduledProperties);
        if (carbonIntensityApi != null) {
            configBuilder.carbonIntensityApi(carbonIntensityApi);
        }
        return configBuilder.build();
    }

    @Bean(name = "greenScheduled")
    public SpringSchedulerFactory springSchedulerFactory() {
        return new SpringSchedulerFactory();
    }

    @Bean
    public SchedulerFactory schedulerFactory() {
        return new SimpleSchedulerFactory();
    }

    @Autowired
    GreenScheduledBeanProcessor greenScheduledBeanProcessor;

    @Bean
    public ScheduledMethodFactory scheduledMethodFactory() {
        return new ScheduledMethodFactory();
    }

    @EventListener
    public void handleContextStart(ContextRefreshedEvent event) {
        var simpleScheduler = (SimpleScheduler) springSchedulerFactory().getObject();
        while (greenScheduledBeanProcessor.hasNext()) {
            var beanInfo = greenScheduledBeanProcessor.next();
            logger.info("Scheduling bean {}", beanInfo.getBean());
            var scheduledMethod = scheduledMethodFactory().create(beanInfo.getBean(), beanInfo.getBeanMethod());
            simpleScheduler.scheduleMethod(scheduledMethod);
        }
    }

    @PreDestroy
    public void stopScheduler() {
        if (simpleScheduler != null) {
            logger.info("Stopping the scheduler.");
            simpleScheduler.stop();
        }
    }
}
