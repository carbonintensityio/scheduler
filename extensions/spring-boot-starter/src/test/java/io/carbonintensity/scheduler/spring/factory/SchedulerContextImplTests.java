package io.carbonintensity.scheduler.spring.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.runtime.ScheduledMethod;

class SchedulerContextImplTests {

    @Test
    void testConstructor() {
        var scheduledMethods = new ArrayList<ScheduledMethod>();
        var scheduleContext = new SchedulerContextImpl(scheduledMethods, true);
        assertThat(scheduleContext.forceSchedulerStart()).isTrue();
        assertThat(scheduleContext.getScheduledMethods()).isEqualTo(scheduledMethods);
    }

}
