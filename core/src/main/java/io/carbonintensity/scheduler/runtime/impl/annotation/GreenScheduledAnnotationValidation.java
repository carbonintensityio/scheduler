package io.carbonintensity.scheduler.runtime.impl.annotation;

import java.util.ArrayList;
import java.util.List;

import com.cronutils.utils.StringUtils;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.observability.GreenObserved;

/**
 * Utility class for validating {@link GreenScheduled} annotations.
 * <p>
 * This class provides a method to check the correctness of a {@link GreenScheduled} annotation
 * and returns a list of validation errors if the configuration is invalid.
 * </p>
 *
 * <p>
 * If any validation rules are violated, a list of error messages is returned.
 * </p>
 *
 * @see GreenScheduled
 */
public class GreenScheduledAnnotationValidation {

    private GreenScheduledAnnotationValidation() {
    }

    static List<String> validateAndReturnValidationErrors(GreenScheduled annotation) {
        List<String> validationErrors = new ArrayList<>();
        if ((annotation.fixedWindow() == null && annotation.successive() == null) ||
                (!StringUtils.isEmpty(annotation.fixedWindow()) && !StringUtils.isEmpty(annotation.successive()))) {
            validationErrors.add("Either fixedWindow or successive must be specified");
        }

        if (StringUtils.isEmpty(annotation.carbonIntensityZone())) {
            validationErrors.add("Zone must be specified");
        }

        if (annotation.fixedWindow() != null && StringUtils.isEmpty(annotation.duration())) {
            validationErrors.add("Duration must be specified when fixedWindow is specified");
        }

        if (!StringUtils.isEmpty(annotation.dayOfMonth()) && !StringUtils.isEmpty(annotation.dayOfWeek())) {
            validationErrors.add("Day of month and day of week can not both be specified ");
        }

        return validationErrors;
    }

    /**
     * Validates the usage of {@link GreenObserved} against the {@link GreenScheduled} schedule(s) declared on the
     * same method.
     * <p>
     * A method can carry more than one {@link GreenScheduled} (via the repeatable {@code GreenSchedules}), each of
     * which becomes its own, independently scheduled job. A single {@link GreenObserved} applies uniformly to all of
     * them, so every one of them is checked individually.
     *
     * @param greenScheduled the {@link GreenScheduled} annotation(s) present on the same method, possibly empty
     * @param greenObserved the {@link GreenObserved} annotation to validate
     * @return the validation errors, empty if the usage is valid
     */
    static List<String> validateGreenObserved(GreenScheduled[] greenScheduled, GreenObserved greenObserved) {
        List<String> validationErrors = new ArrayList<>();

        if (greenScheduled == null || greenScheduled.length == 0) {
            validationErrors.add("@GreenObserved requires @GreenScheduled to be present on the same method");
            return validationErrors;
        }

        if (greenObserved.carbonImpact()) {
            for (GreenScheduled schedule : greenScheduled) {
                if (isCronOnly(schedule)) {
                    validationErrors.add("@GreenObserved(carbonImpact = true) requires the @GreenScheduled schedule '"
                            + schedule.identity()
                            + "' to use fixedWindow or successive; a plain cron-only schedule has no carbon-aware "
                            + "baseline to compare savings against");
                }
            }
        }

        return validationErrors;
    }

    private static boolean isCronOnly(GreenScheduled annotation) {
        return StringUtils.isEmpty(annotation.fixedWindow()) && StringUtils.isEmpty(annotation.successive());
    }

}
