package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.ZonedCarbonIntensityPeriod;
import io.carbonintensity.executionplanner.spi.CarbonIntensityApi;
import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.ScheduledExecution;
import io.carbonintensity.scheduler.Scheduler;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.observability.CarbonImpactResult;
import io.carbonintensity.scheduler.observability.GreenObserved;
import io.carbonintensity.scheduler.test.helper.AnnotationUtil;

/**
 * Tests for {@link CarbonImpactBatchInvoker}: the orchestration around {@link CarbonImpactCalculator} and
 * {@link SimpleScheduler.SimpleTrigger#naiveBaselineFireTime} - fetching (with retry/backoff and per-zone/day
 * sharing), the never-process-today rule, the backlog cutoff, and publishing results only once a day's windows are
 * fully and successfully processed.
 */
class CarbonImpactBatchInvokerTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ZONE = "NL";

    private SimpleScheduler scheduler;
    private ScheduledExecutorService retryExecutor;
    private FakeCarbonIntensityApi api;
    private CopyOnWriteArrayList<CarbonImpactResult> publishedResults;

    @BeforeEach
    void setUp() {
        api = new FakeCarbonIntensityApi();
        retryExecutor = Executors.newSingleThreadScheduledExecutor();
        publishedResults = new CopyOnWriteArrayList<>();

        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(api);
        config.setClock(Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), UTC));
        scheduler = new SimpleScheduler(config);
        scheduler.addJobListener(new Scheduler.EventListener() {
            @Override
            public void jobCarbonImpactCalculated(Trigger trigger, CarbonImpactResult result) {
                publishedResults.add(result);
            }
        });
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        retryExecutor.shutdownNow();
    }

    @Test
    void happyPathPublishesResultAndRemovesTheWindow() throws Exception {
        LocalDate yesterday = LocalDate.of(2026, 9, 4);
        api.respondWith(ZONE, yesterday, flatIntensity(yesterday, 100));

        SimpleScheduler.SimpleTrigger trigger = registerCarbonImpactJob("job-a", true);
        Instant windowStart = yesterday.atStartOfDay(UTC).plusHours(10).toInstant();
        Instant windowEnd = windowStart.plus(Duration.ofMinutes(30));
        scheduler.getCarbonImpactHistory().record("job-a", windowStart, windowEnd);

        newInvoker().invoke(fakeExecution()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(publishedResults).hasSize(1);
        CarbonImpactResult result = publishedResults.get(0);
        assertThat(result.day()).isEqualTo(yesterday);
        assertThat(result.impactGrams()).isGreaterThan(0);
        assertThat(trigger.getLastCarbonImpact()).contains(result);
        assertThat(scheduler.getCarbonImpactHistory().windowsFor("job-a")).isEmpty();
    }

    @Test
    void todaysWindowIsNeverProcessed() throws Exception {
        LocalDate today = LocalDate.of(2026, 9, 5); // matches the fixed clock in setUp()
        registerCarbonImpactJob("job-a", true);
        Instant windowStart = today.atStartOfDay(UTC).plusHours(1).toInstant();
        scheduler.getCarbonImpactHistory().record("job-a", windowStart, windowStart.plusSeconds(60));

        newInvoker().invoke(fakeExecution()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(publishedResults).isEmpty();
        assertThat(scheduler.getCarbonImpactHistory().windowsFor("job-a")).hasSize(1);
        assertThat(api.requestCount(ZONE, today)).isZero();
    }

    @Test
    void multipleJobsInTheSameZoneAndDayShareOneApiCall() throws Exception {
        LocalDate yesterday = LocalDate.of(2026, 9, 4);
        api.respondWith(ZONE, yesterday, flatIntensity(yesterday, 100));

        registerCarbonImpactJob("job-a", true);
        registerCarbonImpactJob("job-b", true);
        Instant start = yesterday.atStartOfDay(UTC).plusHours(5).toInstant();
        scheduler.getCarbonImpactHistory().record("job-a", start, start.plusSeconds(60));
        scheduler.getCarbonImpactHistory().record("job-b", start, start.plusSeconds(60));

        newInvoker().invoke(fakeExecution()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(publishedResults).hasSize(2);
        assertThat(api.requestCount(ZONE, yesterday)).isEqualTo(1);
    }

    @Test
    void transientFailureIsRetriedAndEventuallySucceeds() throws Exception {
        LocalDate yesterday = LocalDate.of(2026, 9, 4);
        api.respondWith(ZONE, yesterday, flatIntensity(yesterday, 100));
        api.failNextNTimes(ZONE, yesterday, 2); // fewer failures than configured retries

        registerCarbonImpactJob("job-a", true);
        Instant start = yesterday.atStartOfDay(UTC).plusHours(5).toInstant();
        scheduler.getCarbonImpactHistory().record("job-a", start, start.plusSeconds(60));

        CarbonImpactBatchInvoker invoker = newInvokerWithBackoffs(List.of(Duration.ofMillis(5), Duration.ofMillis(5)));
        invoker.invoke(fakeExecution()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(publishedResults).hasSize(1);
        assertThat(api.requestCount(ZONE, yesterday)).isEqualTo(3); // 1 initial attempt + 2 retries
        assertThat(scheduler.getCarbonImpactHistory().windowsFor("job-a")).isEmpty();
    }

    @Test
    void windowIsKeptForALaterRunAfterExhaustingAllRetries() throws Exception {
        LocalDate yesterday = LocalDate.of(2026, 9, 4);
        api.respondWith(ZONE, yesterday, flatIntensity(yesterday, 100));
        api.failNextNTimes(ZONE, yesterday, 100); // more failures than configured retries

        registerCarbonImpactJob("job-a", true);
        Instant start = yesterday.atStartOfDay(UTC).plusHours(5).toInstant();
        scheduler.getCarbonImpactHistory().record("job-a", start, start.plusSeconds(60));

        CarbonImpactBatchInvoker invoker = newInvokerWithBackoffs(List.of(Duration.ofMillis(5), Duration.ofMillis(5)));
        invoker.invoke(fakeExecution()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(publishedResults).isEmpty();
        assertThat(scheduler.getCarbonImpactHistory().windowsFor("job-a")).hasSize(1);
    }

    @Test
    void windowBeyondTheBacklogWindowIsAbandonedWithoutCallingTheApi() throws Exception {
        // clock is fixed at 2026-09-05; default backlog window is 7 days, so anything older than 2026-08-28 (today
        // - 7 - 1) is abandoned outright.
        LocalDate tooOld = LocalDate.of(2026, 8, 20);
        registerCarbonImpactJob("job-a", true);
        Instant start = tooOld.atStartOfDay(UTC).plusHours(5).toInstant();
        scheduler.getCarbonImpactHistory().record("job-a", start, start.plusSeconds(60));

        newInvoker().invoke(fakeExecution()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(publishedResults).isEmpty();
        assertThat(scheduler.getCarbonImpactHistory().windowsFor("job-a")).isEmpty();
        assertThat(api.requestCount(ZONE, tooOld)).isZero();
    }

    @Test
    void savingsIsPositiveWhenTheGreenMomentIsCheaperThanTheBaseline() throws Exception {
        LocalDate yesterday = LocalDate.of(2026, 9, 4);
        // hour 2 is cheap (10), hour 10 (the fallback/baseline hour for this fixedWindow job, see
        // GreenScheduledAnnotationParser's daily-average fallback cron) is expensive (500).
        CarbonIntensity intensity = flatIntensity(yesterday, 10);
        intensity.getData().set(10, BigDecimal.valueOf(500));
        api.respondWith(ZONE, yesterday, intensity);

        // fixedWindow 01:00-03:00, with an explicit fallback cron at 10:00 (the expensive hour) - without an
        // explicit cron, the fallback defaults to the window's own midpoint (02:00), which would coincide with the
        // actual fire time below and make this test meaningless (baseline == actual, savings == 0 either way).
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity("job-a")
                .fixedWindow("01:00 03:00")
                .duration("30m")
                .cron("0 0 10 * * ?")
                .carbonIntensityZone(ZONE)
                .timeZone("UTC")
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", "job-a", List.of(greenScheduled),
                greenObserved));

        // the job actually fired at 02:00 (cheap hour) instead of its 10:00 baseline (expensive hour)
        Instant actualStart = yesterday.atStartOfDay(UTC).plusHours(2).toInstant();
        scheduler.getCarbonImpactHistory().record("job-a", actualStart, actualStart.plusSeconds(1800));

        newInvoker().invoke(fakeExecution()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(publishedResults).hasSize(1);
        CarbonImpactResult result = publishedResults.get(0);
        assertThat(result.impactGrams()).isEqualTo(5.0); // 0.5h * 10
        assertThat(result.savingsGrams()).isGreaterThan(0); // baseline (0.5h * 500 = 250) is far more expensive
    }

    private SimpleScheduler.SimpleTrigger registerCarbonImpactJob(String identity, boolean carbonImpact) {
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .identity(identity)
                .successive("0h 1h 1h")
                .duration("30m")
                .carbonIntensityZone(ZONE)
                .build();
        GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(carbonImpact).build();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker(), "Test", identity, List.of(greenScheduled),
                greenObserved));
        return (SimpleScheduler.SimpleTrigger) scheduler.getScheduledJob(identity);
    }

    private CarbonImpactBatchInvoker newInvoker() {
        return newInvokerWithBackoffs(SchedulerDefaults.DEFAULT_CARBON_IMPACT_RETRY_BACKOFFS);
    }

    private CarbonImpactBatchInvoker newInvokerWithBackoffs(List<Duration> backoffs) {
        return new CarbonImpactBatchInvoker(scheduler, scheduler.getCarbonImpactHistory(), new Events(scheduler),
                Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), UTC), api, backoffs,
                SchedulerDefaults.DEFAULT_CARBON_IMPACT_BACKLOG_WINDOW_DAYS, retryExecutor);
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

    private ScheduledInvoker noopInvoker() {
        return execution -> java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    private ScheduledExecution fakeExecution() {
        return new ScheduledExecution() {
            @Override
            public Trigger getTrigger() {
                return null;
            }

            @Override
            public Instant getFireTime() {
                return Instant.now();
            }

            @Override
            public Instant getScheduledFireTime() {
                return Instant.now();
            }
        };
    }

    /**
     * A controllable {@link CarbonIntensityApi}: pre-programmed per zone/day responses, an injectable number of
     * simulated failures before succeeding, and a count of requests actually made per zone/day (to verify the
     * per-zone/day sharing across jobs).
     */
    private static final class FakeCarbonIntensityApi implements CarbonIntensityApi {

        private final Map<String, CarbonIntensity> responses = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> failuresRemaining = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

        void respondWith(String zone, LocalDate date, CarbonIntensity intensity) {
            responses.put(key(zone, date), intensity);
        }

        void failNextNTimes(String zone, LocalDate date, int n) {
            failuresRemaining.put(key(zone, date), new AtomicInteger(n));
        }

        int requestCount(String zone, LocalDate date) {
            AtomicInteger count = requestCounts.get(key(zone, date));
            return count == null ? 0 : count.get();
        }

        @Override
        public java.util.concurrent.CompletableFuture<CarbonIntensity> getCarbonIntensity(ZonedCarbonIntensityPeriod period) {
            LocalDate date = period.getStartTime().toLocalDate();
            String key = key(period.getZone(), date);
            requestCounts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();

            AtomicInteger remaining = failuresRemaining.get(key);
            if (remaining != null && remaining.getAndUpdate(v -> Math.max(0, v - 1)) > 0) {
                return java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("simulated failure"));
            }
            CarbonIntensity response = responses.get(key);
            if (response == null) {
                return java.util.concurrent.CompletableFuture
                        .failedFuture(new IllegalStateException("no fake response configured for " + key));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(response);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        private static String key(String zone, LocalDate date) {
            return zone + "|" + date;
        }
    }
}
