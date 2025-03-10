package io.carbonintensity.scheduler.runtime.impl.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.carbonintensity.executionplanner.planner.fixedwindow.DefaultFixedWindowPlanningConstraints;
import io.carbonintensity.executionplanner.planner.successive.DefaultSuccessivePlanningConstraints;
import io.carbonintensity.executionplanner.spi.PlanningConstraints;
import io.carbonintensity.scheduler.GreenScheduled;

class GreenScheduledAnnotationParserTest {

    @Test
    void shouldCreateFixedWindowConstraintsWhenFixedWindowIsPresent() {
        GreenScheduled annotation = Mockito.mock(GreenScheduled.class);
        Mockito.when(annotation.fixedWindow()).thenReturn("9:30 11:45");
        Mockito.when(annotation.timeZone()).thenReturn("Europe/Amsterdam");
        Mockito.when(annotation.duration()).thenReturn("15m");
        Mockito.when(annotation.zone()).thenReturn("NL");

        PlanningConstraints constraints = GreenScheduledAnnotationParser.createConstraints("testJob", annotation,
                Clock.systemDefaultZone());

        assertThat(constraints).isInstanceOf(DefaultFixedWindowPlanningConstraints.class);
        DefaultFixedWindowPlanningConstraints fixedConstraints = (DefaultFixedWindowPlanningConstraints) constraints;
        assertThat(fixedConstraints.getDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(fixedConstraints.getZone()).isEqualTo("NL");
    }

    @Test
    void shouldCreateSuccessiveConstraintsWhenSuccessiveIsPresent() {
        GreenScheduled annotation = Mockito.mock(GreenScheduled.class);
        Mockito.when(annotation.successive()).thenReturn("0h 2h 6h");
        Mockito.when(annotation.duration()).thenReturn("30m");
        Mockito.when(annotation.zone()).thenReturn("US");
        ZonedDateTime now = ZonedDateTime.now();
        Clock clock = Clock.fixed(now.toInstant(),
                ZoneId.of("UTC"));

        PlanningConstraints constraints = GreenScheduledAnnotationParser.createConstraints("testJob", annotation, clock);

        assertThat(constraints).isInstanceOf(DefaultSuccessivePlanningConstraints.class);
        DefaultSuccessivePlanningConstraints successiveConstraints = (DefaultSuccessivePlanningConstraints) constraints;
        assertThat(successiveConstraints.getInitialMaximumDelay()).isEqualTo(Duration.ZERO);
        assertThat(successiveConstraints.getInitialStartTime()).isEqualTo(now);
        assertThat(successiveConstraints.getMinimumGap()).isEqualTo(Duration.ofHours(2));
        assertThat(successiveConstraints.getMaximumGap()).isEqualTo(Duration.ofHours(6));
        assertThat(successiveConstraints.getDuration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(successiveConstraints.getZone()).isEqualTo("US");
    }

    @Test
    void shouldThrowExceptionWhenNeitherFixedWindowNorSuccessiveIsPresent() {
        GreenScheduled annotation = Mockito.mock(GreenScheduled.class);
        Mockito.when(annotation.fixedWindow()).thenReturn("");
        Mockito.when(annotation.successive()).thenReturn("");

        assertThatThrownBy(() -> GreenScheduledAnnotationParser.createConstraints("testJob", annotation, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Found 2 validation errors while creating GreenScheduled constraints for testJob: \n" +
                        "Zone must be specified\n" +
                        "Duration must be specified when fixedWindow is specified");
    }

    @Test
    void shouldParseOverdueGracePeriodCorrectly() {
        GreenScheduled annotation = Mockito.mock(GreenScheduled.class);
        Mockito.when(annotation.overdueGracePeriod()).thenReturn("PT10M");

        Duration gracePeriod = GreenScheduledAnnotationParser.parseOverdueGracePeriod(annotation, Duration.ofMinutes(5));
        assertThat(gracePeriod).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldReturnDefaultGracePeriodWhenNotSet() {
        GreenScheduled annotation = Mockito.mock(GreenScheduled.class);
        Mockito.when(annotation.overdueGracePeriod()).thenReturn("");

        Duration gracePeriod = GreenScheduledAnnotationParser.parseOverdueGracePeriod(annotation, Duration.ofMinutes(5));
        assertThat(gracePeriod).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void shouldThrowExceptionForInvalidOverdueGracePeriod() {
        GreenScheduled annotation = Mockito.mock(GreenScheduled.class);
        Mockito.when(annotation.overdueGracePeriod()).thenReturn("invalid");
        Duration duration = Duration.ofMinutes(5);

        assertThatThrownBy(() -> GreenScheduledAnnotationParser.parseOverdueGracePeriod(annotation, duration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ISO 8601 duration format");
    }
}
