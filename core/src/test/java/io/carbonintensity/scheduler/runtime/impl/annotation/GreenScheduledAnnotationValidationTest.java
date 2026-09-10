package io.carbonintensity.scheduler.runtime.impl.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.observability.GreenObserved;
import io.carbonintensity.scheduler.test.helper.AnnotationUtil;

class GreenScheduledAnnotationValidationTest {

    @Test
    void shouldRejectGreenObservedWithoutAnyGreenScheduled() {
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().build();

        List<String> errors = GreenScheduledAnnotationValidation.validateGreenObserved(new GreenScheduled[0], greenObserved);

        assertThat(errors).containsExactly("@GreenObserved requires @GreenScheduled to be present on the same method");
    }

    @Test
    void shouldAcceptGreenObservedWithoutCarbonImpactRegardlessOfSchedule() {
        GreenScheduled cronOnly = AnnotationUtil.newGreenScheduled().cron("0 0 * * * ?").carbonIntensityZone("NL").build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(false).build();

        List<String> errors = GreenScheduledAnnotationValidation.validateGreenObserved(new GreenScheduled[] { cronOnly },
                greenObserved);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldAcceptCarbonImpactWithFixedWindowSchedule() {
        GreenScheduled fixedWindow = AnnotationUtil.newGreenScheduled()
                .fixedWindow("9:30 11:45")
                .duration("15m")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        List<String> errors = GreenScheduledAnnotationValidation.validateGreenObserved(new GreenScheduled[] { fixedWindow },
                greenObserved);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldAcceptCarbonImpactWithSuccessiveSchedule() {
        GreenScheduled successive = AnnotationUtil.newGreenScheduled()
                .successive("0h 2h 6h")
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        List<String> errors = GreenScheduledAnnotationValidation.validateGreenObserved(new GreenScheduled[] { successive },
                greenObserved);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldRejectCarbonImpactWithCronOnlySchedule() {
        GreenScheduled cronOnly = AnnotationUtil.newGreenScheduled()
                .identity("midnight-check")
                .cron("0 0 0 * * ?")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        List<String> errors = GreenScheduledAnnotationValidation.validateGreenObserved(new GreenScheduled[] { cronOnly },
                greenObserved);

        assertThat(errors).containsExactly(
                "@GreenObserved(carbonImpact = true) requires the @GreenScheduled schedule 'midnight-check' to use "
                        + "fixedWindow or successive; a plain cron-only schedule has no carbon-aware baseline to "
                        + "compare savings against");
    }

    @Test
    void shouldRejectCarbonImpactWhenAnyRepeatedScheduleIsCronOnly() {
        GreenScheduled fixedWindow = AnnotationUtil.newGreenScheduled()
                .identity("green-run")
                .fixedWindow("9:30 11:45")
                .duration("15m")
                .carbonIntensityZone("NL")
                .build();
        GreenScheduled cronOnly = AnnotationUtil.newGreenScheduled()
                .identity("fallback-run")
                .cron("0 0 12 * * ?")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        List<String> errors = GreenScheduledAnnotationValidation.validateGreenObserved(
                new GreenScheduled[] { fixedWindow, cronOnly }, greenObserved);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("fallback-run");
    }
}
