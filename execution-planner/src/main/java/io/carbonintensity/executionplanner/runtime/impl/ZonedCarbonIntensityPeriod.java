package io.carbonintensity.executionplanner.runtime.impl;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Zoned {@link CarbonIntensity} period.
 */
public class ZonedCarbonIntensityPeriod {

    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;
    private final String zone;

    private ZonedCarbonIntensityPeriod(Builder builder) {
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.zone = builder.zone;
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

        public ZonedCarbonIntensityPeriod build() {
            Objects.requireNonNull(startTime, "startTime is required");
            Objects.requireNonNull(endTime, "endTime is required");
            Objects.requireNonNull(zone, "zoneId is required");
            return new ZonedCarbonIntensityPeriod(this);
        }

    }
}
