package io.carbonintensity.executionplanner.planner.fixedwindow;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.cronutils.model.Cron;

/**
 * Data class containing the constraints that are used by {@link FixedWindowPlanner} to plan the best window
 */
public class DefaultFixedWindowPlanningConstraints extends FixedWindowPlanningConstraints {

    private final String identity;
    private final Duration duration;
    private final String zone;
    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;
    private final Cron fallbackCronExpression;
    private final ZoneId timeZoneId;

    public DefaultFixedWindowPlanningConstraints(String identity,
            Duration duration,
            String zone,
            ZonedDateTime startTime,
            ZonedDateTime endTime,
            Cron fallbackCronExpression,
            ZoneId timeZoneId) {
        this.identity = identity;
        this.duration = duration;
        this.zone = zone;
        this.startTime = startTime;
        this.endTime = endTime;
        this.fallbackCronExpression = fallbackCronExpression;
        this.timeZoneId = timeZoneId;
    }

    @Override
    public String getIdentity() {
        return identity;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public String getZone() {
        return zone;
    }

    @Override
    public ZonedDateTime getStart() {
        return startTime;
    }

    @Override
    public ZonedDateTime getEnd() {
        return endTime;
    }

    @Override
    public ZoneId getTimeZoneId() {
        return timeZoneId;
    }

    @Override
    public Cron getFallbackCronExpression() {
        return this.fallbackCronExpression;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DefaultFixedWindowPlanningConstraints.Builder from(FixedWindowPlanningConstraints constraints) {
        return new DefaultFixedWindowPlanningConstraints.Builder()
                .withIdentity(constraints.getIdentity())
                .withDuration(constraints.getDuration())
                .withZone(constraints.getZone())
                .withStart(constraints.getStart())
                .withEnd(constraints.getEnd())
                .withFallbackCronExpression(constraints.getFallbackCronExpression())
                .withTimeZoneId(constraints.getTimeZoneId());

    }

    public static final class Builder {
        private String identity;
        private Duration duration;
        private String zone;
        private ZonedDateTime startTime;
        private ZonedDateTime endTime;
        private Cron fallbackCronExpression;
        private ZoneId timeZoneId;

        private Builder() {
        }

        public Builder withIdentity(String identity) {
            this.identity = identity;
            return this;
        }

        public Builder withDuration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder withZone(String zone) {
            this.zone = zone;
            return this;
        }

        public Builder withStart(ZonedDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder withFallbackCronExpression(Cron fallbackCronExpression) {
            this.fallbackCronExpression = fallbackCronExpression;
            return this;
        }

        public Builder withEnd(ZonedDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder withTimeZoneId(ZoneId timeZoneId) {
            this.timeZoneId = timeZoneId;
            return this;
        }

        public DefaultFixedWindowPlanningConstraints build() {
            return new DefaultFixedWindowPlanningConstraints(identity, duration, zone, startTime, endTime,
                    fallbackCronExpression, timeZoneId);
        }
    }
}
