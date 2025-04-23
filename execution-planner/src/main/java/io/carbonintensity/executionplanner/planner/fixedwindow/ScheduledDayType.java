package io.carbonintensity.executionplanner.planner.fixedwindow;

import java.util.EnumSet;

public enum ScheduledDayType {
    EVERY_DAY,
    EVERY_WORKDAY,
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
    DAY_1,
    DAY_2,
    DAY_3,
    DAY_4,
    DAY_5,
    DAY_6,
    DAY_7,
    DAY_8,
    DAY_9,
    DAY_10,
    DAY_11,
    DAY_12,
    DAY_13,
    DAY_14,
    DAY_15,
    DAY_16,
    DAY_17,
    DAY_18,
    DAY_19,
    DAY_20,
    DAY_21,
    DAY_22,
    DAY_23,
    DAY_24,
    DAY_25,
    DAY_26,
    DAY_27,
    DAY_28,
    DAY_29,
    DAY_30,
    DAY_31;

    static final EnumSet<ScheduledDayType> daysOfMonth = EnumSet.range(DAY_1, DAY_31);
    static final EnumSet<ScheduledDayType> daysOfWeek = EnumSet.range(MONDAY, SUNDAY);

    static int getDay(ScheduledDayType scheduledDayType) {
        if (daysOfMonth.contains(scheduledDayType)) {
            String s = scheduledDayType.toString();
            s = s.substring(s.lastIndexOf("_") + 1);
            return Integer.parseInt(s);
        }
        return 0;
    }

}
