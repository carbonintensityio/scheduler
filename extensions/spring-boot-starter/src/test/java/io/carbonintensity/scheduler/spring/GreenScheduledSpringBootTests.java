package io.carbonintensity.scheduler.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;

import io.carbonintensity.scheduler.Scheduler;

/**
 * This test works without {@link org.springframework.boot.test.context.runner.ApplicationContextRunner}.
 * Green Scheduler Auto-configuration should work out of the box.
 */
@SpringBootTest(classes = { GreenScheduledSpringBootTests.TestApplication.class })
class GreenScheduledSpringBootTests {

    @Autowired
    Scheduler scheduler;

    @Test
    @DirtiesContext
    void givenDefaultContext_whenJobIsDefined_thenScheduleJob() {
        assertThat(scheduler).isNotNull();
        assertThat(scheduler.getScheduledJobs()).hasSize(1);

    }

    @EnableAutoConfiguration
    public static class TestApplication {

        @Bean
        TestScheduledJob testJob() {
            return new TestScheduledJob();
        }
    }

}
