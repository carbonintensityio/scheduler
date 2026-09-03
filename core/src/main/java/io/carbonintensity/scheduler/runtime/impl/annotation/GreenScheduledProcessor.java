package io.carbonintensity.scheduler.runtime.impl.annotation;

import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.observability.GreenObserved;

/**
 * Annotation processor for {@link GreenScheduled} and {@link GreenObserved}.
 * <p>
 * This processor validates the usage of both annotations at compile time. It ensures that annotated methods comply
 * with the expected constraints by using {@link GreenScheduledAnnotationValidation}.
 * <p>
 * If validation errors are found, they are reported as compile-time errors using {@link Messager}.
 * </p>
 * <p>
 * Because this processor is registered as a standard {@code javax.annotation.processing.Processor} (see
 * {@code META-INF/services}), these checks run for any project that has {@code core} on its annotation processor
 * path - not just Quarkus, Spring, or Micronaut consumers using their own extension-specific validation.
 * </p>
 *
 * <p>
 * This processor supports Java 17.
 * </p>
 *
 * @see GreenScheduled
 * @see GreenObserved
 * @see GreenScheduledAnnotationValidation
 */
@SupportedAnnotationTypes({
        "io.carbonintensity.scheduler.GreenScheduled",
        "io.carbonintensity.scheduler.observability.GreenObserved"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class GreenScheduledProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Messager messager = processingEnv.getMessager();

        for (Element element : roundEnv.getElementsAnnotatedWith(GreenScheduled.class)) {
            if (element instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) element;
                // GreenScheduled is @Repeatable: getAnnotation() returns null when applied more than once,
                // so every schedule is read via getAnnotationsByType() instead, and validated individually.
                for (GreenScheduled schedule : executableElement.getAnnotationsByType(GreenScheduled.class)) {
                    report(messager, executableElement,
                            GreenScheduledAnnotationValidation.validateAndReturnValidationErrors(schedule));
                }
            }
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(GreenObserved.class)) {
            if (element instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) element;
                GreenScheduled[] schedules = executableElement.getAnnotationsByType(GreenScheduled.class);
                GreenObserved greenObserved = executableElement.getAnnotation(GreenObserved.class);

                report(messager, executableElement,
                        GreenScheduledAnnotationValidation.validateGreenObserved(schedules, greenObserved));
            }
        }
        return true;
    }

    private static void report(Messager messager, ExecutableElement element, List<String> validationErrors) {
        for (String validationError : validationErrors) {
            messager.printMessage(Diagnostic.Kind.ERROR, validationError, element);
        }
    }
}