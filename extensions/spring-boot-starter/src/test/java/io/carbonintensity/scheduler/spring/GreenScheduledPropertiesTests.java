package io.carbonintensity.scheduler.spring;

import static io.carbonintensity.scheduler.spring.GreenScheduledProperties.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.runtime.SchedulerConfig;

class GreenScheduledPropertiesTests {

    @Test
    void whenCreatingProperties_thenSetDefaultValues() {
        GreenScheduledProperties properties = new GreenScheduledProperties();

        assertThat(properties.getEnabled()).hasValue(true);
        assertThat(properties.getJobExecutors()).hasValue(DEFAULT_NUMBER_OF_JOB_EXECUTORS);
        assertThat(properties.getOverdueGracePeriod()).hasValue(DEFAULT_OVERDUE_GRACE_PERIOD);
        assertThat(properties.getShutdownGracePeriod()).hasValue(DEFAULT_SHUTDOWN_GRADE_PERIOD);
        assertThat(properties.getApiUrl()).hasValue(DEFAULT_API_URL);
        assertThat(properties.getApiKey()).isNotPresent();
    }

    @Test
    void whenOverridingDefaultValues_thenSetOverriddenValues() {
        GreenScheduledProperties properties = new GreenScheduledProperties(true, SchedulerConfig.StartMode.HALTED, 1,
                Duration.ofSeconds(1), Duration.ofSeconds(2), "apiKey", "apiUrl");

        assertThat(properties.getEnabled()).hasValue(true);
        assertThat(properties.getJobExecutors()).hasValue(1);
        assertThat(properties.getOverdueGracePeriod()).hasValue(Duration.ofSeconds(1));
        assertThat(properties.getShutdownGracePeriod()).hasValue(Duration.ofSeconds(2));
        assertThat(properties.getApiUrl()).hasValue("apiUrl");
        assertThat(properties.getApiKey()).hasValue("apiKey");
    }

}
