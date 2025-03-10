package io.carbonintensity.executionplanner.planner.fixedwindow;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.cronutils.model.Cron;

import io.carbonintensity.executionplanner.spi.PlanningConstraints;

public abstract class FixedWindowPlanningConstraints implements PlanningConstraints {
    public abstract ZonedDateTime getStart();

    public abstract ZonedDateTime getEnd();

    public abstract ZoneId getTimeZoneId();

    public abstract Cron getFallbackCronExpression();
}
