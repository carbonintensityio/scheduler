package io.carbonintensity.scheduler.spring.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.spring.TestScheduledJob;

class ScheduledMethodFactoryTests {

    TestScheduledJob testJob = new TestScheduledJob();
    ScheduledMethodFactory factory = new ScheduledMethodFactory();
    Method method;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        method = TestScheduledJob.class.getMethod("run");
    }

    @Test
    void testCreate() {
        var scheduledMethod = factory.create(testJob, method);
        assertThat(scheduledMethod).isNotNull();
        assertThat(scheduledMethod.getMethodName()).isEqualTo("run");
        assertThat(scheduledMethod.getDeclaringClassName()).isEqualTo(TestScheduledJob.class.getName());
        assertThat(scheduledMethod.getSchedules()).hasSize(1);
    }

    @Test
    void testGetGreenScheduledAnnotations() {
        var scheduledMethod = ScheduledMethodFactory.getGreenScheduledAnnotations(method);
        assertThat(scheduledMethod)
                .isNotNull()
                .hasSize(1);
    }

}
