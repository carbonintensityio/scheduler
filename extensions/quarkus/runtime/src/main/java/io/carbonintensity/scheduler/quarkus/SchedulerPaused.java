package io.carbonintensity.scheduler.quarkus;

/**
 * This event is fired synchronously and asynchronously when the {@link Scheduler#pause()} method is called.
 */
public class SchedulerPaused {

    public static final SchedulerPaused INSTANCE = new SchedulerPaused();

}
