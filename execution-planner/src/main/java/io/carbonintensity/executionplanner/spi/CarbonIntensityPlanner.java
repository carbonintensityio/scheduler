package io.carbonintensity.executionplanner.spi;

public interface CarbonIntensityPlanner<T extends PlanningConstraints> {

    boolean canSchedule(T constraints);

    /**
     * @return the chosen fire time and its carbon-intensity value, or
     *         {@code null} if no timeslot could be found
     */
    PlannedExecution getNextExecutionTime(T constraints);
}
