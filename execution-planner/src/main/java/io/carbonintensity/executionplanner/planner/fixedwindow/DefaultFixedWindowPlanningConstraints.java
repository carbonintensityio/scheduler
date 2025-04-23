package io.carbonintensity.executionplanner.planner.fixedwindow;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    private final ScheduledDayType scheduledDayType;

    public DefaultFixedWindowPlanningConstraints(String identity,
            Duration duration,
            String zone,
            ZonedDateTime startTime,
            ZonedDateTime endTime,
            Cron fallbackCronExpression,
            ZoneId timeZoneId,
            ScheduledDayType scheduledDayType) {
        this.identity = identity;
        this.duration = duration;
        this.zone = zone;
        int delayDays = 0;
        if (!checkStartTime(startTime, scheduledDayType)) {
            delayDays = delayedTime(startTime, scheduledDayType);
        }
        this.startTime = startTime.plusDays(delayDays);
        this.endTime = endTime.plusDays(delayDays);
        this.fallbackCronExpression = fallbackCronExpression;
        this.timeZoneId = timeZoneId;
        this.scheduledDayType = scheduledDayType;
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
    public ScheduledDayType getScheduledDayType() {
        return scheduledDayType;
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
                .withScheduledDayType(constraints.getScheduledDayType())
                .withFallbackCronExpression(constraints.getFallbackCronExpression())
                .withTimeZoneId(constraints.getTimeZoneId());

    }

    private static boolean checkStartTime(ZonedDateTime s, ScheduledDayType scheduledDayType) {
        if (ScheduledDayType.daysOfWeek.contains(scheduledDayType)) {
            return (DayOfWeek.valueOf(scheduledDayType.name()) == s.getDayOfWeek());
        } else if (scheduledDayType == ScheduledDayType.EVERY_WORKDAY) {
            return (s.getDayOfWeek().getValue() <= 5);
        } else if (ScheduledDayType.daysOfMonth.contains(scheduledDayType)) {
            return (checkMonth(s, scheduledDayType));
        }
        return true;
    }

    private static boolean checkMonth(ZonedDateTime s, ScheduledDayType scheduledDayType) {
        Set<Integer> monthsWith30Days = new HashSet<>(Arrays.asList(4, 6, 9, 11));
        int shouldBeDay = ScheduledDayType.getDay(scheduledDayType);

        if (shouldBeDay > 28 && s.getMonthValue() == 2) {
            return s.getDayOfMonth() == 28;
        } else if (shouldBeDay == 31 && monthsWith30Days.contains(s.getMonthValue())) {
            return s.getDayOfMonth() == 30;
        }
        return s.getMonthValue() == shouldBeDay;
    }

    private static int delayedTime(ZonedDateTime s, ScheduledDayType scheduledDayType) {
        if (ScheduledDayType.daysOfWeek.contains(scheduledDayType)) {
            return (Math.floorMod(((DayOfWeek.valueOf(scheduledDayType.name()).getValue() - s.getDayOfWeek().getValue())), 7));
        }
        if (scheduledDayType == ScheduledDayType.EVERY_WORKDAY && s.getDayOfWeek().getValue() == 6) {
            return 2;
        }
        if (ScheduledDayType.daysOfMonth.contains(scheduledDayType)) {
            return calculateMonth(s, scheduledDayType);
        }
        return 1;
    }

    private static int calculateMonth(ZonedDateTime s, ScheduledDayType scheduledDayType) {
        int delay;
        int shouldBeDay = ScheduledDayType.getDay(scheduledDayType);
        int setDay = s.getDayOfMonth();

        if (shouldBeDay > 28 && s.getMonthValue() == 2) {
            return 28 - setDay;
        }

        delay = shouldBeDay - setDay;

        Set<Integer> monthsWith30Days = new HashSet<>(Arrays.asList(4, 6, 9, 11));
        if (shouldBeDay < setDay) {
            delay += 31;
            if (monthsWith30Days.contains(s.getMonthValue())) {
                delay--;
            }
            if (s.getMonthValue() == 2) {
                delay -= 3;
            }
        }

        if (shouldBeDay > setDay && shouldBeDay == 31 && monthsWith30Days.contains(s.getMonthValue())) {
            delay--;
        }

        return delay;
    }

    public static final class Builder {
        private String identity;
        private Duration duration;
        private String zone;
        private ZonedDateTime startTime;
        private ZonedDateTime endTime;
        private Cron fallbackCronExpression;
        private ZoneId timeZoneId;
        private ScheduledDayType scheduledDayType;
        private int delay = 0;

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
            if (!checkStartTime(startTime, scheduledDayType)) {
                delay = delayedTime(startTime, scheduledDayType);
            }
            this.startTime = startTime.plusDays(delay);
            return this;
        }

        public Builder withFallbackCronExpression(Cron fallbackCronExpression) {
            this.fallbackCronExpression = fallbackCronExpression;
            return this;
        }

        public Builder withEnd(ZonedDateTime endTime) {
            this.endTime = endTime.plusDays(delay);
            return this;
        }

        public Builder withTimeZoneId(ZoneId timeZoneId) {
            this.timeZoneId = timeZoneId;
            return this;
        }

        public Builder withScheduledDayType(ScheduledDayType scheduledDayType) {
            this.scheduledDayType = scheduledDayType;
            return this;
        }

        public DefaultFixedWindowPlanningConstraints build() {
            return new DefaultFixedWindowPlanningConstraints(identity, duration, zone, startTime, endTime,
                    fallbackCronExpression, timeZoneId, scheduledDayType);
        }

    }
}
