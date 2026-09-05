package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
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

    @Test
    void triggerShouldExposeTheCarbonIntensityZoneFromTheCoLocatedGreenScheduled() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 1h 2h")
                .duration("30m")
                .carbonIntensityZone("BE")
                .build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "test", List.of(greenScheduled)));

        SimpleScheduler.SimpleTrigger trigger = (SimpleScheduler.SimpleTrigger) scheduler.getScheduledJob("test");

        assertThat(trigger.getCarbonIntensityZone()).isEqualTo("BE");
    }

    @Test
    void schedulingACarbonImpactEnabledJobRegistersTheBatchTrigger() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 1h 2h")
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        scheduler = newScheduler();
        assertThat(scheduler.getScheduledJob(CarbonImpactBatchTrigger.IDENTITY)).isNull();

        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "test",
                List.of(greenScheduled), greenObserved));

        assertThat(scheduler.getScheduledJob(CarbonImpactBatchTrigger.IDENTITY)).isNotNull();
    }

    @Test
    void schedulingAJobWithoutCarbonImpactDoesNotRegisterTheBatchTrigger() {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("test")
                .successive("0h 1h 2h")
                .duration("30m")
                .carbonIntensityZone("NL")
                .build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "test", List.of(greenScheduled)));

        assertThat(scheduler.getScheduledJob(CarbonImpactBatchTrigger.IDENTITY)).isNull();
    }

    @Test
    void schedulingASecondCarbonImpactEnabledJobDoesNotRegisterTheBatchTriggerTwice() {
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

        scheduler = newScheduler();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "first",
                List.of(AnnotationUtil.newGreenScheduled().identity("first").successive("0h 1h 2h").duration("30m")
                        .carbonIntensityZone("NL").build()),
                greenObserved));
        Trigger firstBatchTrigger = scheduler.getScheduledJob(CarbonImpactBatchTrigger.IDENTITY);

        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "second",
                List.of(AnnotationUtil.newGreenScheduled().identity("second").successive("0h 1h 2h").duration("30m")
                        .carbonIntensityZone("NL").build()),
                greenObserved));
        Trigger secondBatchTrigger = scheduler.getScheduledJob(CarbonImpactBatchTrigger.IDENTITY);

        assertThat(secondBatchTrigger).isSameAs(firstBatchTrigger);
    }

    @Test
    void concurrentFirstRegistrationsDoNotLeakTheLosingRetryExecutor() throws Exception {
        scheduler = newScheduler();
        int concurrency = 8;
        ExecutorService racers = Executors.newFixedThreadPool(concurrency);
        CyclicBarrier barrier = new CyclicBarrier(concurrency);
        try {
            List<CompletableFuture<Void>> registrations = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                String identity = "racer-" + i;
                registrations.add(CompletableFuture.runAsync(() -> {
                    GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                            .identity(identity)
                            .successive("0h 1h 2h")
                            .duration("30m")
                            .carbonIntensityZone("NL")
                            .build();
                    GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();
                    awaitBarrier(barrier);
                    scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", identity,
                            List.of(greenScheduled), greenObserved));
                }, racers));
            }
            CompletableFuture.allOf(registrations.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

            assertThat(scheduler.getScheduledJob(CarbonImpactBatchTrigger.IDENTITY)).isNotNull();
            scheduler.close();
            scheduler = null; // already closed - avoid a redundant afterEach close()

            // proves the fix: every executor created by a losing registration (if the race was actually hit) was
            // shut down immediately instead of being left running forever
            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(retryThreadsStillAlive()).isEmpty());
        } finally {
            racers.shutdownNow();
        }
    }

    private static List<String> retryThreadsStillAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith("green-scheduler-carbon-impact-retry"))
                .collect(Collectors.toList());
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
