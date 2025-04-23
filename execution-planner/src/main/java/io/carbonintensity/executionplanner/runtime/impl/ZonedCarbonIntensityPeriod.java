package io.carbonintensity.executionplanner.runtime.impl;

import java.time.ZonedDateTime;
import java.util.Objects;

import io.carbonintensity.executionplanner.planner.fixedwindow.ScheduledDayType;

/**
 * Zoned {@link CarbonIntensity} period.
 */
public class ZonedCarbonIntensityPeriod {

    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;
    private final String zone;
    private final ScheduledDayType scheduledDayType;

    private ZonedCarbonIntensityPeriod(Builder builder) {
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.zone = builder.zone;
        this.scheduledDayType = builder.scheduledDayType;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public String getZone() {
        return zone;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }

    public ScheduledDayType getScheduledDayType() {
        return scheduledDayType;
    }

    @Override
    public String toString() {
        return "ZonedCarbonIntensityPeriod{" +
                "zone='" + zone + '\'' +
                ", endTime=" + endTime +
                ", startTime=" + startTime +
                '}';
    }

    public static class Builder {

        private ZonedDateTime startTime;
        private ZonedDateTime endTime;
        private String zone;
        private ScheduledDayType scheduledDayType;

        public Builder withStartTime(ZonedDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder withEndTime(ZonedDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder withZone(String zone) {
            this.zone = zone;
            return this;
        }

        public Builder withScheduledDayType(ScheduledDayType scheduledDayType) {
            this.scheduledDayType = scheduledDayType;
            return this;
        }

        public ZonedCarbonIntensityPeriod build() {
            Objects.requireNonNull(startTime, "startTime is required");
            Objects.requireNonNull(endTime, "endTime is required");
            Objects.requireNonNull(zone, "zoneId is required");
            return new ZonedCarbonIntensityPeriod(this);
        }

    }
}
