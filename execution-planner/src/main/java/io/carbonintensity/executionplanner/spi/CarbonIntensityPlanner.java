package io.carbonintensity.executionplanner.spi;

import java.time.ZonedDateTime;

public interface CarbonIntensityPlanner<T extends PlanningConstraints> {

    boolean canSchedule(T constraints);

    ZonedDateTime getNextExecutionTime(T constraints);
}
