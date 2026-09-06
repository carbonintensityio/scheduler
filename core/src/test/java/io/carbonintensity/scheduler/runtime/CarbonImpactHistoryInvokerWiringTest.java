package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.observability.ExecutionWindow;
import io.carbonintensity.scheduler.observability.GreenObserved;
import io.carbonintensity.scheduler.test.helper.AnnotationUtil;
import io.carbonintensity.scheduler.test.helper.DisabledDummyCarbonIntensityApi;

/**
 * Verifies that a {@code carbonImpact}-enabled job's successful executions actually land in
 * {@link SimpleScheduler#getCarbonImpactHistory()} - not just that the wiring compiles.
 * <p>
 * Uses a {@code successive} schedule with the carbon-intensity API disabled, which falls back to a plain interval
 * trigger (the average of the min/max gap) - a pure {@code cron}-only {@link GreenScheduled} is not actually
 * schedulable today ({@code GreenScheduledAnnotationParser.createConstraints} throws "Not yet implemented" for it),
 * which is an unrelated, pre-existing gap.
 */
class CarbonImpactHistoryInvokerWiringTest {

    private SimpleScheduler scheduler;

    @AfterEach
    void afterEach() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Test
    void successfulExecutionOfCarbonImpactEnabledJobIsRecorded() {
        AtomicInteger invocations = new AtomicInteger();
        ScheduledInvoker invoker = execution -> {
            invocations.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        };

        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0s 1s 1s")
                .duration("1s")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(invoker, "Test", "test",
                List.of(greenScheduled), greenObserved));
        scheduler.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> invocations.get() > 0);
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> !scheduler.getCarbonImpactHistory().windowsFor("test").isEmpty());

        List<ExecutionWindow> windows = scheduler.getCarbonImpactHistory().windowsFor("test");
        assertThat(windows).isNotEmpty();
        assertThat(windows.get(0).end()).isAfterOrEqualTo(windows.get(0).start());
    }

    @Test
    void failedExecutionIsNotRecorded() {
        ScheduledInvoker invoker = execution -> CompletableFuture.failedFuture(new RuntimeException("boom"));

        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0s 1s 1s")
                .duration("1s")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(invoker, "Test", "test",
                List.of(greenScheduled), greenObserved));
        scheduler.start();

        await().pollDelay(Duration.ofSeconds(2)).atMost(5, TimeUnit.SECONDS).until(() -> true);

        assertThat(scheduler.getCarbonImpactHistory().windowsFor("test")).isEmpty();
    }

    @Test
    void executionWithoutCarbonImpactEnabledIsNotRecorded() {
        AtomicInteger invocations = new AtomicInteger();
        ScheduledInvoker invoker = execution -> {
            invocations.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        };

        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0s 1s 1s")
                .duration("1s")
                .carbonIntensityZone("NL")
                .build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(invoker, "Test", "test", List.of(greenScheduled)));
        scheduler.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> invocations.get() > 0);

        assertThat(scheduler.getCarbonImpactHistory().windowsFor("test")).isEmpty();
    }

    private SimpleScheduler newScheduler() {
        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(new DisabledDummyCarbonIntensityApi());
        config.setClock(Clock.systemUTC());
        return new SimpleScheduler(config);
    }
}
