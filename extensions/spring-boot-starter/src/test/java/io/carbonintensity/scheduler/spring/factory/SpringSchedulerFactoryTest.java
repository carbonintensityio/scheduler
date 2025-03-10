package io.carbonintensity.scheduler.spring.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.carbonintensity.scheduler.Scheduler;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;

@ExtendWith(MockitoExtension.class)
class SpringSchedulerFactoryTest {

    private SpringSchedulerFactory springFactory;

    @Mock
    SchedulerFactory schedulerFactory;

    @Mock
    SchedulerConfig schedulerConfig;

    @Mock
    Scheduler.EventListener jobListener;

    @Mock
    Scheduler scheduler;

    @BeforeEach
    public void setup() {
        springFactory = new SpringSchedulerFactory();
        springFactory.setSchedulerFactory(schedulerFactory);
        springFactory.setSchedulerConfig(schedulerConfig);
        springFactory.setJobListener(jobListener);

    }

    @Test
    void givenJobListener_whenNotNull_thenAddListener() {
        when(schedulerFactory.createScheduler(schedulerConfig)).thenReturn(scheduler);
        assertThat(springFactory.getObject()).isEqualTo(scheduler);
        verify(scheduler).addJobListener(jobListener);
    }

    @Test
    void givenJobListener_whenNull_thenDontAddListener() {
        springFactory.setJobListener(null);
        when(schedulerFactory.createScheduler(schedulerConfig)).thenReturn(scheduler);
        verifyNoMoreInteractions(scheduler);
        assertThat(springFactory.getObject()).isEqualTo(scheduler);
    }
}
