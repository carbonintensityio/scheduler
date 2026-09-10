package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.Scheduler;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.observability.CarbonImpactHistoryStore;
import io.carbonintensity.scheduler.observability.CarbonImpactResult;
import io.carbonintensity.scheduler.observability.ExecutionWindow;
import io.carbonintensity.scheduler.observability.GreenObserved;
import io.carbonintensity.scheduler.test.helper.AnnotationUtil;
import io.carbonintensity.scheduler.test.helper.MutableClock;

/**
 * An end-to-end test of the whole carbon-impact machinery (CIIO-347), driven entirely through the real
 * {@link SimpleScheduler} tick - no manual {@link CarbonImpactHistoryStore#record} or direct
 * {@link CarbonImpactBatchInvoker#invoke} call anywhere - with only the outbound carbonintensity-api call replaced
 * by {@link FakeCarbonIntensityApi}:
 * <ol>
 * <li>scheduling a {@code carbonImpact}-enabled job auto-registers the {@link CarbonImpactBatchTrigger}, before it
 * has ever fired;</li>
 * <li>a real {@code FixedWindowTrigger} fire is captured by the real {@link CarbonImpactHistoryInvoker} into
 * {@link CarbonImpactHistoryStore};</li>
 * <li>a real {@link CarbonImpactBatchTrigger} fire the next day dispatches a real
 * {@link CarbonImpactBatchInvoker#invoke} on the scheduler's own job executor;</li>
 * <li>which fetches (from the fake), computes, and publishes a {@link CarbonImpactResult} through the real
 * {@link Scheduler.EventListener} mechanism, and removes the processed window from history.</li>
 * </ol>
 * The exact numeric correctness of the calculation itself (bucket-weighting, baseline zone/day handling, retry
 * behavior) is covered in detail by {@link CarbonImpactCalculatorTest} and {@link CarbonImpactBatchInvokerTest};
 * this test's job is to prove the wiring between all of these pieces is real, not to re-derive that math.
 */
class CarbonImpactEndToEndTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ZONE = "NL";

    private SimpleScheduler scheduler;

    @AfterEach
    void afterEach() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Test
    void aRealFireIsCapturedBatchedAndPublishedEndToEnd() throws Exception {
        FakeCarbonIntensityApi api = new FakeCarbonIntensityApi();
        CopyOnWriteArrayList<CarbonImpactResult> publishedResults = new CopyOnWriteArrayList<>();
        LocalDate day1 = LocalDate.of(2024, 6, 1);

        MutableClock mutableClock = new MutableClock(Clock.fixed(day1.atStartOfDay(UTC).toInstant(), UTC));
        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(api);
        config.setClock(mutableClock);
        scheduler = new SimpleScheduler(config);
        scheduler.addJobListener(new Scheduler.EventListener() {
            @Override
            public void jobCarbonImpactCalculated(Trigger trigger, CarbonImpactResult result) {
                publishedResults.add(result);
            }
        });

        // a window and fallback hour deliberately clear of the default 02:00-06:00 UTC carbon-impact batch window,
        // so the batch's own daily fire (auto-registered below) never overlaps with this job's own fixed window
        CountDownLatch invocationStarted = new CountDownLatch(1);
        CountDownLatch releaseInvocation = new CountDownLatch(1);
        ScheduledInvoker realWorkInvoker = execution -> {
            invocationStarted.countDown();
            try {
                releaseInvocation.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return CompletableFuture.completedFuture(null);
        };
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("real-job")
                .fixedWindow("12:00 12:30")
                .duration("10m")
                .cron("0 0 20 * * ?") // fallback/naive-baseline hour: 20:00 UTC
                .overdueGracePeriod("PT2H")
                .carbonIntensityZone(ZONE)
                .timeZone("UTC")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(realWorkInvoker, "Test", "real-job",
                List.of(greenScheduled), greenObserved));

        // the batch is auto-registered the moment a carbonImpact-enabled job is scheduled - before it has ever fired
        assertThat(scheduler.getScheduledJob(CarbonImpactBatchTrigger.IDENTITY)).isNotNull();

        mutableClock.getNotifier().register(scheduler);
        scheduler.start();

        // shift just past the fixed window's end (comfortably inside its own 2h overdue grace period) so the real
        // trigger fires, regardless of exactly which slot within it the planner picked
        mutableClock.shift(Duration.ofHours(12).plusMinutes(31));
        assertThat(invocationStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // advance the clock by exactly 10 minutes while the real invocation is still in flight, then let it
        // complete - CarbonImpactHistoryInvoker measures real wall-clock start/end, so this produces a real,
        // non-zero 10-minute execution window instead of a degenerate zero-length one
        mutableClock.shift(Duration.ofMinutes(10));
        releaseInvocation.countDown();

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(scheduler.getCarbonImpactHistory().windowsFor("real-job")).hasSize(1));
        ExecutionWindow window = scheduler.getCarbonImpactHistory().windowsFor("real-job").get(0);
        assertThat(Duration.between(window.start(), window.end())).isEqualTo(Duration.ofMinutes(10));
        LocalDate executionDay = window.start().atZone(UTC).toLocalDate();
        assertThat(executionDay).isEqualTo(day1);

        // cheap at the actual execution hour (12), expensive at the naive-baseline hour (20) - so a real, positive
        // savings figure only comes out if the real fetch/calculate/baseline pipeline is actually wired correctly
        CarbonIntensity intensity = flatIntensity(day1, 10);
        intensity.getData().set(20, BigDecimal.valueOf(500));
        api.respondWith(ZONE, day1, intensity);
        api.respondWith(ZONE, day1.plusDays(1), intensity); // defensive: in case the baseline ever lands a day later

        // shift into the next day's carbon-impact batch window (02:00-06:00 UTC, comfortably past the widest
        // possible jitter) - the real CarbonImpactBatchTrigger fires and dispatches a real invoke() on the
        // scheduler's own job executor, which fetches from the fake, computes, and publishes
        mutableClock.shift(Duration.ofHours(17).plusMinutes(20));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(publishedResults).hasSize(1));

        CarbonImpactResult result = publishedResults.get(0);
        assertThat(result.day()).isEqualTo(day1);
        assertThat(result.impactGrams()).isCloseTo(10.0 / 60 * 10, within(0.001)); // 10 min @ 10 gCO2/kWh
        assertThat(result.savingsGrams()).isCloseTo(10.0 / 60 * (500 - 10), within(0.001)); // baseline (500) - actual (10)
        assertThat(scheduler.getCarbonImpactHistory().windowsFor("real-job")).isEmpty();

        SimpleScheduler.SimpleTrigger trigger = (SimpleScheduler.SimpleTrigger) scheduler.getScheduledJob("real-job");
        assertThat(trigger.getLastCarbonImpact()).contains(result);
    }

    private static CarbonIntensity flatIntensity(LocalDate date, int value) {
        CarbonIntensity intensity = new CarbonIntensity();
        intensity.setStart(date.atStartOfDay(UTC).toInstant());
        intensity.setResolution(Duration.ofHours(1));
        List<BigDecimal> data = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            data.add(BigDecimal.valueOf(value));
        }
        intensity.setData(data);
        return intensity;
    }
}
