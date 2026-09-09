package io.carbonintensity.executionplanner.runtime.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The data from the carbon intensity API: a list of co2eq's per watt in timed blocks, with a start, end and a resolution.
 */
public class CarbonIntensity {

    private Duration resolution;
    private String zone;
    private Instant start;
    private Instant end;
    private List<BigDecimal> data = new ArrayList<>();

    public final Duration getResolution() {
        return resolution;
    }

    public final void setResolution(Duration resolution) {
        this.resolution = resolution;
    }

    public final String getZone() {
        return zone;
    }

    public final void setZone(String zone) {
        this.zone = zone;
    }

    public final boolean hasData() {
        return data != null && !data.isEmpty();
    }

    public final List<BigDecimal> getData() {
        return data;
    }

    public final void setData(List<BigDecimal> data) {
        this.data = data;
    }

    public final Instant getStart() {
        return start;
    }

    public final void setStart(Instant start) {
        this.start = start;
    }

    public final Instant getEnd() {
        return end;
    }

    public final void setEnd(Instant end) {
        this.end = end;
    }

    @Override
    public final String toString() {
        return "CarbonIntensity{" +
                "resolution=" + resolution +
                ", zone='" + zone + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", data=" + data +
                '}';
    }
}
