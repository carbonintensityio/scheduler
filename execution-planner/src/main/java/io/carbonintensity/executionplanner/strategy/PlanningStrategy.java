package io.carbonintensity.executionplanner.strategy;

import java.time.Duration;
import java.time.ZonedDateTime;

import io.carbonintensity.executionplanner.planner.Timeslot;
import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;

public interface PlanningStrategy {
    Timeslot bestTimeslot(ZonedDateTime ws, ZonedDateTime we, Duration duration, CarbonIntensity carbonIntensity);
}
