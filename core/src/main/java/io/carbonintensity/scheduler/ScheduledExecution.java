package io.carbonintensity.scheduler;

import java.time.Instant;

/**
 * Execution metadata of a specific scheduled job.
 */
public interface ScheduledExecution {

    /**
     *
     * @return the trigger that fired the execution
     */
    Trigger getTrigger();

    /**
     * The returned {@code Instant} is converted from the date-time in the default timezone.
     * <p>
     * Unlike {@link Trigger#getPreviousFireTime()} this method always returns the same value.
     *
     * @return the time the associated trigger was fired
     */
    Instant getFireTime();

    /**
     * The returned {@code Instant} is converted from the date-time in the default timezone.
     * <p>
     *
     * @return the time the associated trigger was scheduled to fire
     */
    Instant getScheduledFireTime();

}
