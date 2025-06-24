package io.carbonintensity.scheduler.spring.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.runtime.SchedulerConfig;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.carbonintensity.scheduler.spring.TestScheduledJob;

class SimpleSchedulerFactoryTests {

    final SchedulerConfig schedulerConfig = new SchedulerConfigBuilder()
            .enabled(true)
            .build();
    Method method;
    SimpleSchedulerFactory factory;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        method = TestScheduledJob.class.getMethod("run");
        factory = new SimpleSchedulerFactory();
    }

    @Test
    void whenCreatingScheduler_thenCreateSimpleScheduler() {
        var scheduler = factory.createScheduler(schedulerConfig);
        assertThat(scheduler)
                .isNotNull()
                .isInstanceOf(SimpleScheduler.class);
    }
}
