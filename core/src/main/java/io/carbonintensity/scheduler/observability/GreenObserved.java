package io.carbonintensity.scheduler.observability;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.carbonintensity.scheduler.GreenScheduled;

/**
 * Opts a {@link GreenScheduled} method into observability data: fire-time/status transparency (last/next fire
 * time, execution outcome and duration, scheduling drift, ...) is always collected once this annotation is present.
 * <p>
 * A method annotated with {@code @GreenObserved} must also be annotated with {@link GreenScheduled}. Absence of
 * this annotation means no observability data is collected for the job.
 *
 * <pre>
 * &#64;GreenScheduled(fixedWindow = "9:30 11:45", duration = "15m", carbonIntensityZone = "NL")
 * &#64;GreenObserved
 * void check() {
 *     // fire-time/status data is now collected for this job.
 * }
 * </pre>
 *
 * @see GreenScheduled
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface GreenObserved {

    /**
     * Additionally enables carbon-impact and savings metrics for this job: the actual carbon impact of each run and
     * the savings versus a job-specific naive baseline, computed retrospectively (never for the current day).
     * <p>
     * Requires the co-located {@link GreenScheduled} to be configured with {@code fixedWindow} or {@code successive}
     * - a plain {@code cron}-only schedule has no carbon-aware alternative to compare against, so combining
     * {@code carbonImpact = true} with a cron-only {@link GreenScheduled} fails at build time.
     *
     * @return whether carbon-impact/savings metrics are collected, in addition to fire-time/status data
     */
    boolean carbonImpact() default false;

}
