package io.carbonintensity.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.executionplanner.planner.fixedwindow.FixedWindowPlanner;
import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensityDataFetcher;
import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensityDataFetcherImpl;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApi;
import io.carbonintensity.scheduler.runtime.ImmutableScheduledMethod;
import io.carbonintensity.scheduler.runtime.ScheduledInvoker;
import io.carbonintensity.scheduler.runtime.SchedulerConfig;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.carbonintensity.scheduler.runtime.impl.rest.CarbonIntensityFileApi;
import io.carbonintensity.scheduler.test.helper.DisabledDummyCarbonIntensityApi;
import io.carbonintensity.scheduler.test.helper.MutableClock;

class TestFixedWindowScheduler {

    private static final Logger log = LoggerFactory.getLogger(TestFixedWindowScheduler.class);
    public static final long SCHEDULER_WAITING_PERIOD = 101L;
    private SimpleScheduler scheduler;
    private final CarbonIntensityApi disabledApi = new DisabledDummyCarbonIntensityApi();

    private final CarbonIntensityDataFetcher dataFetcher = new CarbonIntensityDataFetcherImpl(disabledApi,
            new CarbonIntensityFileApi());

    @AfterEach
    public void afterEach() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    @Test
    void testFixedWindowScheduler() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(2);

        ScheduledInvoker scheduledCountDownInvoker = execution -> {
            try {
                log.info("Invoked scheduledCountDownInvoker executing countdown...");
                cdl.countDown();
                return CompletableFuture.completedStage(null);
            } catch (Exception e) {
                return CompletableFuture.failedStage(e);
            }
        };

        GreenScheduled greenScheduled = mock(GreenScheduled.class);
        when(greenScheduled.fixedWindow()).thenReturn("05:15 08:15");
        when(greenScheduled.zone()).thenReturn("NL");
        when(greenScheduled.duration()).thenReturn("2h");
        when(greenScheduled.identity()).thenReturn("test");
        when(greenScheduled.overdueGracePeriod()).thenReturn("PT90S");
        when(greenScheduled.timeZone()).thenReturn("Europe/Amsterdam");
        when(greenScheduled.skipExecutionIf()).thenAnswer(invocationOnMock -> SkipPredicate.Never.class);

        ImmutableScheduledMethod immutableScheduledMethod = new ImmutableScheduledMethod(
                scheduledCountDownInvoker,
                this.getClass().getName(),
                "testFixedWindowScheduler",
                List.of(greenScheduled));

        ZoneId zone = ZoneId.of("UTC");
        // Create a mutable clock so that we can properly simulate running through a fixedTimeFrame
        MutableClock mutableClock = new MutableClock(
                Clock.fixed(ZonedDateTime
                        .of(LocalDateTime.of(LocalDate.now(), LocalTime.of(4, 16)), ZoneId.of("Europe/Amsterdam")).toInstant(),
                        zone));

        scheduler = new SimpleScheduler(TestSchedulerContext.builder()
                .withScheduledMethod(immutableScheduledMethod)
                .build(), new SchedulerConfig(), dataFetcher, null,
                mutableClock);
        mutableClock.getNotifier().register(scheduler);

        scheduler.start();

        Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, according to the schedule, it should not run.
        Assertions.assertThat(cdl.getCount()).isEqualTo(2);

        // shift the clock to 6:16 which within the window but is before the "most green time", so it still not run
        mutableClock.shift(Duration.ofHours(2));
        Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, according to the schedule, it should not run.
        Assertions.assertThat(cdl.getCount()).isEqualTo(2);

        // shift the clock to 7:16 which is at the "most green time", so it should run
        mutableClock.shift(Duration.ofHours(1));

        Awaitility.waitAtMost(SCHEDULER_WAITING_PERIOD, TimeUnit.MILLISECONDS)
                .until(() -> cdl.getCount() == 1);

        mutableClock.shift(Duration.ofMinutes(4));
        Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, it should not run twice
        Assertions.assertThat(cdl.getCount()).isEqualTo(1);

        mutableClock.shift(Duration.ofDays(1));
        Awaitility.waitAtMost(SCHEDULER_WAITING_PERIOD, TimeUnit.MILLISECONDS)
                .until(() -> cdl.getCount() == 0);
        Assertions.assertThat(cdl.getCount()).isZero();
    }

    @Test
    void testFixedWindowScheduler_useFallbackCron() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);

        ScheduledInvoker scheduledCountDownInvoker = execution -> {
            try {
                log.info("Invoked scheduledCountDownInvoker executing countdown...");
                cdl.countDown();
                return CompletableFuture.completedStage(null);
            } catch (Exception e) {
                return CompletableFuture.failedStage(e);
            }
        };

        GreenScheduled greenScheduled = mock(GreenScheduled.class);
        when(greenScheduled.fixedWindow()).thenReturn("05:15 08:15");
        when(greenScheduled.zone()).thenReturn("NL");
        when(greenScheduled.duration()).thenReturn("2h");
        when(greenScheduled.identity()).thenReturn("test");
        when(greenScheduled.cron()).thenReturn("0 15 10 * * ?");
        when(greenScheduled.overdueGracePeriod()).thenReturn("PT90S");
        when(greenScheduled.timeZone()).thenReturn("Europe/Amsterdam");
        when(greenScheduled.skipExecutionIf()).thenAnswer(invocationOnMock -> SkipPredicate.Never.class);

        ImmutableScheduledMethod immutableScheduledMethod = new ImmutableScheduledMethod(
                scheduledCountDownInvoker,
                this.getClass().getName(),
                "testFixedWindowScheduler",
                List.of(greenScheduled));

        ZoneId zone = ZoneId.of("UTC");
        // Create a mutable clock so that we can properly simulate running through a fixedTimeFrame
        MutableClock mutableClock = new MutableClock(
                Clock.fixed(ZonedDateTime
                        .of(LocalDateTime.of(LocalDate.now(), LocalTime.of(4, 16)), ZoneId.of("Europe/Amsterdam")).toInstant(),
                        zone));

        try (MockedConstruction<FixedWindowPlanner> fixedWindowPlannerMockedConstruction = mockConstruction(
                FixedWindowPlanner.class, (mock, context) -> when(mock.canSchedule(any())).thenReturn(false))) {
            scheduler = new SimpleScheduler(TestSchedulerContext.builder()
                    .withScheduledMethod(immutableScheduledMethod)
                    .build(), new SchedulerConfig(), dataFetcher, null,
                    mutableClock);
            mutableClock.getNotifier().register(scheduler);
            scheduler.start();

            Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, according to the schedule, it should not run.
            Assertions.assertThat(cdl.getCount()).isEqualTo(1);

            // shift the clock to 8:16, since we cannot schedule we still won't run.
            mutableClock.shift(Duration.ofHours(4));
            Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, according to the schedule, it should not run.
            Assertions.assertThat(cdl.getCount()).isEqualTo(1);

            // shift the clock to 10:16, according to our cron it should run at 10:15
            mutableClock.shift(Duration.ofHours(2));

            Awaitility.waitAtMost(SCHEDULER_WAITING_PERIOD, TimeUnit.MILLISECONDS)
                    .until(() -> cdl.getCount() == 0);

            mutableClock.shift(Duration.ofMinutes(4));
            Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, it should not run twice
            Assertions.assertThat(cdl.getCount()).isZero();
        }
    }

    @Test
    void testFixedWindowScheduler_useCalculatedFallbackCron() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);

        ScheduledInvoker scheduledCountDownInvoker = execution -> {
            try {
                log.info("Invoked scheduledCountDownInvoker executing countdown...");
                cdl.countDown();
                return CompletableFuture.completedStage(null);
            } catch (Exception e) {
                return CompletableFuture.failedStage(e);
            }
        };

        GreenScheduled greenScheduled = mock(GreenScheduled.class);
        when(greenScheduled.fixedWindow()).thenReturn("05:15 08:15"); // setting no fallback cron, so default will become 6:40
        when(greenScheduled.zone()).thenReturn("NL");
        when(greenScheduled.duration()).thenReturn("2h");
        when(greenScheduled.identity()).thenReturn("test");
        when(greenScheduled.overdueGracePeriod()).thenReturn("PT90S");
        when(greenScheduled.timeZone()).thenReturn("Europe/Amsterdam");
        when(greenScheduled.skipExecutionIf()).thenAnswer(invocationOnMock -> SkipPredicate.Never.class);

        ImmutableScheduledMethod immutableScheduledMethod = new ImmutableScheduledMethod(
                scheduledCountDownInvoker,
                this.getClass().getName(),
                "testFixedWindowScheduler",
                List.of(greenScheduled));

        ZoneId zone = ZoneId.of("UTC");
        // Create a mutable clock so that we can properly simulate running through a fixedTimeFrame
        MutableClock mutableClock = new MutableClock(
                Clock.fixed(ZonedDateTime
                        .of(LocalDateTime.of(LocalDate.now(), LocalTime.of(4, 44)), ZoneId.of("Europe/Amsterdam")).toInstant(),
                        zone));

        try (MockedConstruction<FixedWindowPlanner> fixedWindowPlannerMockedConstruction = mockConstruction(
                FixedWindowPlanner.class, (mock, context) -> when(mock.canSchedule(any())).thenReturn(false))) {
            scheduler = new SimpleScheduler(TestSchedulerContext.builder()
                    .withScheduledMethod(immutableScheduledMethod)
                    .build(), new SchedulerConfig(), dataFetcher, null,
                    mutableClock);
            mutableClock.getNotifier().register(scheduler);
            scheduler.start();

            Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, according to the schedule, it should not run.
            Assertions.assertThat(cdl.getCount()).isEqualTo(1);

            // shift the clock to 6:44, since we cannot schedule we still won't run.
            mutableClock.shift(Duration.ofHours(2));
            Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, according to the schedule, it should not run.
            Assertions.assertThat(cdl.getCount()).isEqualTo(1);

            // shift the clock to 6:46, according to the calculated cron it should run at 6:45
            mutableClock.shift(Duration.ofMinutes(2));

            Awaitility.waitAtMost(SCHEDULER_WAITING_PERIOD, TimeUnit.MILLISECONDS)
                    .until(() -> cdl.getCount() == 0);

            mutableClock.shift(Duration.ofMinutes(4));
            Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, it should not run twice
            Assertions.assertThat(cdl.getCount()).isZero();
        }
    }

    @Test
    void testFixedWindowScheduler_executedDueToGracePeriod() {
        CountDownLatch cdl = new CountDownLatch(1);

        ScheduledInvoker scheduledCountDownInvoker = execution -> {
            try {
                log.info("Invoked scheduledCountDownInvoker executing countdown...");
                cdl.countDown();
                return CompletableFuture.completedStage(null);
            } catch (Exception e) {
                return CompletableFuture.failedStage(e);
            }
        };

        GreenScheduled greenScheduled = mock(GreenScheduled.class);
        when(greenScheduled.fixedWindow()).thenReturn("05:15 08:15");
        when(greenScheduled.zone()).thenReturn("NL");
        when(greenScheduled.duration()).thenReturn("2h");
        when(greenScheduled.identity()).thenReturn("test");
        when(greenScheduled.overdueGracePeriod()).thenReturn("PT90S");
        when(greenScheduled.timeZone()).thenReturn("Europe/Amsterdam");
        when(greenScheduled.skipExecutionIf()).thenAnswer(invocationOnMock -> SkipPredicate.Never.class);

        ImmutableScheduledMethod immutableScheduledMethod = new ImmutableScheduledMethod(
                scheduledCountDownInvoker,
                this.getClass().getName(),
                "testFixedWindowScheduler_executedDueToGracePeriod",
                List.of(greenScheduled));

        Clock fixedClock = Clock
                .fixed(ZonedDateTime
                        .of(LocalDateTime.of(LocalDate.now(), LocalTime.of(8, 15, 30)), ZoneId.of("Europe/Amsterdam"))
                        .toInstant(), ZoneId.systemDefault());

        scheduler = new SimpleScheduler(TestSchedulerContext.builder()
                .withScheduledMethod(immutableScheduledMethod)
                .build(), new SchedulerConfig(), dataFetcher, null,
                fixedClock);

        Assertions.assertThat(cdl.getCount()).isEqualTo(1);
        scheduler.start();

        Awaitility.waitAtMost(2000L, TimeUnit.MILLISECONDS)
                .until(() -> cdl.getCount() == 0);
        Assertions.assertThat(cdl.getCount()).isZero();
    }

    @Test
    void testFixedWindowScheduler_neverExecutedDueToSkipOfTimeFrame() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);

        ScheduledInvoker scheduledCountDownInvoker = execution -> {
            try {
                log.info("Invoked scheduledCountDownInvoker executing countdown...");
                cdl.countDown();
                return CompletableFuture.completedStage(null);
            } catch (Exception e) {
                return CompletableFuture.failedStage(e);
            }
        };

        GreenScheduled greenScheduled = mock(GreenScheduled.class);
        when(greenScheduled.fixedWindow()).thenReturn("05:15 08:15");
        when(greenScheduled.zone()).thenReturn("NL");
        when(greenScheduled.duration()).thenReturn("2h");
        when(greenScheduled.identity()).thenReturn("test");
        when(greenScheduled.overdueGracePeriod()).thenReturn("PT90S");
        when(greenScheduled.timeZone()).thenReturn("Europe/Amsterdam");
        when(greenScheduled.skipExecutionIf()).thenAnswer(invocationOnMock -> SkipPredicate.Never.class);

        ImmutableScheduledMethod immutableScheduledMethod = new ImmutableScheduledMethod(
                scheduledCountDownInvoker,
                this.getClass().getName(),
                "testFixedWindowScheduler_neverExecutedDueToSkipOfTimeFrame",
                List.of(greenScheduled));

        //  Set the time just after endTime (8:15) + overDueGracePeriod (90s)
        Clock fixedClock = Clock
                .fixed(ZonedDateTime.of(LocalDateTime.of(LocalDate.now(), LocalTime.of(8, 17)), ZoneId.of("Europe/Amsterdam"))
                        .toInstant(),
                        ZoneId.systemDefault());

        scheduler = new SimpleScheduler(TestSchedulerContext.builder()
                .withScheduledMethod(immutableScheduledMethod)
                .build(), new SchedulerConfig(), dataFetcher, null,
                fixedClock);

        Assertions.assertThat(cdl.getCount()).isEqualTo(1);
        scheduler.start();

        Thread.sleep(SCHEDULER_WAITING_PERIOD); // Wait a few seconds, to give the scheduler time assess if it should run.
        Assertions.assertThat(cdl.getCount()).isEqualTo(1);
    }

    @Test
    void testFixedWindowOvernightScheduler() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);

        ScheduledInvoker scheduledCountDownInvoker = execution -> {
            try {
                log.info("Invoked scheduledCountDownInvoker executing countdown...");
                cdl.countDown();
                return CompletableFuture.completedStage(null);
            } catch (Exception e) {
                return CompletableFuture.failedStage(e);
            }
        };

        GreenScheduled greenScheduled = mock(GreenScheduled.class);
        when(greenScheduled.fixedWindow()).thenReturn("23:15 02:15");
        when(greenScheduled.zone()).thenReturn("NL");
        when(greenScheduled.duration()).thenReturn("2h");
        when(greenScheduled.identity()).thenReturn("test");
        when(greenScheduled.overdueGracePeriod()).thenReturn("PT90S");
        when(greenScheduled.timeZone()).thenReturn("Europe/Amsterdam");
        when(greenScheduled.skipExecutionIf()).thenAnswer(invocationOnMock -> SkipPredicate.Never.class);

        ImmutableScheduledMethod immutableScheduledMethod = new ImmutableScheduledMethod(
                scheduledCountDownInvoker,
                this.getClass().getName(),
                "testFixedWindowOvernightScheduler",
                List.of(greenScheduled));

        ZoneId zone = ZoneId.of("UTC");
        // Create a mutable clock so that we can properly simulate running through a fixedTimeFrame
        MutableClock mutableClock = new MutableClock(
                Clock.fixed(ZonedDateTime
                        .of(LocalDateTime.of(LocalDate.now(), LocalTime.of(22, 16)), ZoneId.of("Europe/Amsterdam")).toInstant(),
                        zone));

        scheduler = new SimpleScheduler(TestSchedulerContext.builder()
                .withScheduledMethod(immutableScheduledMethod)
                .build(), new SchedulerConfig(), dataFetcher, null,
                mutableClock);
        mutableClock.getNotifier().register(scheduler);
        scheduler.start();

        Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, according to the schedule, it should not run.
        Assertions.assertThat(cdl.getCount()).isEqualTo(1);

        // shift the clock to 00:16 which within the window but is before the "most green time", so it still not run
        mutableClock.shift(Duration.ofHours(2));
        Thread.sleep(SCHEDULER_WAITING_PERIOD); // Sleep a few seconds, according to the schedule, it should not run.
        Assertions.assertThat(cdl.getCount()).isEqualTo(1);

        // shift the clock to 1:16 which is at the "most green time", so it should run
        mutableClock.shift(Duration.ofHours(1));

        Awaitility.waitAtMost(SCHEDULER_WAITING_PERIOD, TimeUnit.MILLISECONDS)
                .until(() -> cdl.getCount() == 0);
        Assertions.assertThat(cdl.getCount()).isZero();
    }
}
