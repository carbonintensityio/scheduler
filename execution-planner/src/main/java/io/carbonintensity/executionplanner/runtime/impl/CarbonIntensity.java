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

    public Duration getResolution() {
        return resolution;
    }

    public void setResolution(Duration resolution) {
        this.resolution = resolution;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    public List<BigDecimal> getData() {
        return data;
    }

    public void setData(List<BigDecimal> data) {
        this.data = data;
    }

    public Instant getStart() {
        return start;
    }

    public void setStart(Instant start) {
        this.start = start;
    }

    public Instant getEnd() {
        return end;
    }

    public void setEnd(Instant end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "CarbonIntensity{" +
                "resolution=" + resolution +
                ", zone='" + zone + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", data=" + data +
                '}';
    }
}
