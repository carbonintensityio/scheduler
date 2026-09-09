package io.carbonintensity.scheduler.micronaut;

import java.time.Duration;

import io.carbonintensity.scheduler.runtime.SchedulerConfig;
import io.carbonintensity.scheduler.runtime.SchedulerDefaults;
import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration properties for the green scheduler, bound from the {@code green-scheduler.*} namespace.
 */
@ConfigurationProperties(GreenSchedulerConfigurationProperties.PREFIX)
public class GreenSchedulerConfigurationProperties {

    public static final String PREFIX = "green-scheduler";

    private boolean enabled = true;
    private SchedulerConfig.StartMode startMode = SchedulerConfig.StartMode.NORMAL;
    private int jobExecutors = SchedulerDefaults.DEFAULT_NUMBER_OF_JOB_EXECUTORS;
    private Duration overdueGracePeriod = SchedulerDefaults.DEFAULT_OVERDUE_GRACE_PERIOD;
    private Duration shutdownGracePeriod = SchedulerDefaults.DEFAULT_SHUTDOWN_GRACE_PERIOD;
    private String apiUrl = SchedulerDefaults.DEFAULT_API_URL;
    private String apiKey;

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public final SchedulerConfig.StartMode getStartMode() {
        return startMode;
    }

    public final void setStartMode(SchedulerConfig.StartMode startMode) {
        this.startMode = startMode;
    }

    public final int getJobExecutors() {
        return jobExecutors;
    }

    public final void setJobExecutors(int jobExecutors) {
        this.jobExecutors = jobExecutors;
    }

    public final Duration getOverdueGracePeriod() {
        return overdueGracePeriod;
    }

    public final void setOverdueGracePeriod(Duration overdueGracePeriod) {
        this.overdueGracePeriod = overdueGracePeriod;
    }

    public final Duration getShutdownGracePeriod() {
        return shutdownGracePeriod;
    }

    public final void setShutdownGracePeriod(Duration shutdownGracePeriod) {
        this.shutdownGracePeriod = shutdownGracePeriod;
    }

    public final String getApiUrl() {
        return apiUrl;
    }

    public final void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public final String getApiKey() {
        return apiKey;
    }

    public final void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
