package io.carbonintensity.executionplanner.planner.successive;

import java.time.Duration;
import java.time.ZonedDateTime;

import io.carbonintensity.executionplanner.spi.PlanningConstraints;

public interface SuccessivePlanningConstraints extends PlanningConstraints {
    ZonedDateTime getLastExecutionTime();

    ZonedDateTime getInitialStartTime();

    Duration getInitialMaximumDelay();

    Duration getMinimumGap();

    Duration getMaximumGap();
}