package io.carbonintensity.scheduler.runtime.impl.annotation;

import java.util.ArrayList;
import java.util.List;

import io.carbonintensity.scheduler.GreenScheduled;

/**
 * Utility class for validating {@link GreenScheduled} annotations.
 * <p>
 * This class provides a method to check the correctness of a {@link GreenScheduled} annotation
 * and return a list of validation errors if the configuration is invalid.
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
                (annotation.fixedWindow() != null && !annotation.fixedWindow().isEmpty() &&
                        annotation.successive() != null && !annotation.successive().isEmpty())) {
            validationErrors.add("Either fixedWindow or successive must be specified");
        }

        if (annotation.zone() == null || annotation.zone().isEmpty()) {
            validationErrors.add("Zone must be specified");
        }

        if (annotation.fixedWindow() != null && (annotation.duration() == null || annotation.duration().isEmpty())) {
            validationErrors.add("Duration must be specified when fixedWindow is specified");
        }
        return validationErrors;
    }
}
