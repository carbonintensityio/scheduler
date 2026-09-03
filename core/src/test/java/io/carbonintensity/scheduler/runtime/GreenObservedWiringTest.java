package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.observability.CarbonImpactResult;
import io.carbonintensity.scheduler.observability.GreenObserved;
import io.carbonintensity.scheduler.test.helper.AnnotationUtil;
import io.carbonintensity.scheduler.test.helper.DisabledDummyCarbonIntensityApi;

/**
 * Verifies that a {@link GreenObserved} passed on a {@link ScheduledMethod} actually reaches the resulting
 * {@link Trigger} - the wiring introduced for the upcoming carbon-impact batch (CIIO-347).
 */
class GreenObservedWiringTest {

    private SimpleScheduler scheduler;

    @AfterEach
    void afterEach() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Test
    void triggerShouldReportCarbonImpactEnabledWhenGreenObservedRequestsIt() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 1h 2h")
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "test",
                List.of(greenScheduled), greenObserved));

        SimpleScheduler.SimpleTrigger trigger = (SimpleScheduler.SimpleTrigger) scheduler.getScheduledJob("test");

        assertThat(trigger.isCarbonImpactEnabled()).isTrue();
    }

    @Test
    void triggerShouldNotReportCarbonImpactEnabledWhenGreenObservedIsAbsent() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 1h 2h")
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "test", List.of(greenScheduled)));

        SimpleScheduler.SimpleTrigger trigger = (SimpleScheduler.SimpleTrigger) scheduler.getScheduledJob("test");

        assertThat(trigger.isCarbonImpactEnabled()).isFalse();
    }

    @Test
    void triggerShouldNotReportCarbonImpactEnabledWhenGreenObservedHasItDisabled() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 1h 2h")
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(false).build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "test",
                List.of(greenScheduled), greenObserved));

        SimpleScheduler.SimpleTrigger trigger = (SimpleScheduler.SimpleTrigger) scheduler.getScheduledJob("test");

        assertThat(trigger.isCarbonImpactEnabled()).isFalse();
    }

    @Test
    void getLastCarbonImpactShouldBeEmptyUntilSet() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 1h 2h")
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "test",
                List.of(greenScheduled), greenObserved));

        SimpleScheduler.SimpleTrigger trigger = (SimpleScheduler.SimpleTrigger) scheduler.getScheduledJob("test");
        assertThat(trigger.getLastCarbonImpact()).isEmpty();

        CarbonImpactResult result = new CarbonImpactResult(LocalDate.of(2026, 9, 2), 12.5, 4.0,
                Instant.now().truncatedTo(ChronoUnit.SECONDS));
        trigger.setLastCarbonImpact(result);

        assertThat(trigger.getLastCarbonImpact()).contains(result);
    }

    private SimpleScheduler newScheduler() {
        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(new DisabledDummyCarbonIntensityApi());
        return new SimpleScheduler(config);
    }

    private ScheduledInvoker noopInvoker() {
        return execution -> CompletableFuture.completedFuture(null);
    }
}
