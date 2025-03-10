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

/**
 * Annotation processor for {@link GreenScheduled}.
 * <p>
 * This processor validates the usage of the {@link GreenScheduled} annotation at compile time.
 * It ensures that annotated methods comply with the expected constraints by using
 * {@link GreenScheduledAnnotationValidation}.
 * <p>
 * If validation errors are found, they are reported as compile-time errors using {@link Messager}.
 * </p>
 *
 * <p>
 * This processor supports Java 11 and processes the {@code io.carbonintensity.scheduler.GreenScheduled} annotation.
 * </p>
 *
 * @see GreenScheduled
 * @see GreenScheduledAnnotationValidation
 */
@SupportedAnnotationTypes("io.carbonintensity.scheduler.GreenScheduled")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class GreenScheduledProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Messager messager = processingEnv.getMessager();

        for (Element element : roundEnv.getElementsAnnotatedWith(GreenScheduled.class)) {
            if (element instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) element;
                GreenScheduled annotation = executableElement.getAnnotation(GreenScheduled.class);

                List<String> validationErrors = GreenScheduledAnnotationValidation
                        .validateAndReturnValidationErrors(annotation);

                for (String validationError : validationErrors) {
                    messager.printMessage(Diagnostic.Kind.ERROR, validationError, executableElement);
                }
            }
        }
        return true;
    }
}