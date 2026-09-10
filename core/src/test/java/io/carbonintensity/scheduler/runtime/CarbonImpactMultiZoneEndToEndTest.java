package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.Scheduler;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.observability.CarbonImpactResult;
import io.carbonintensity.scheduler.observability.GreenObserved;
import io.carbonintensity.scheduler.test.helper.AnnotationUtil;
import io.carbonintensity.scheduler.test.helper.MutableClock;

/**
 * An end-to-end test of the carbon-impact machinery (CIIO-347) with several jobs registered at once - a mix of
 * {@code FixedWindowTrigger} and {@code SuccessiveTrigger} jobs, one per supported zone - to prove the batch
 * correctly enumerates and processes every {@code carbonImpact}-enabled job regardless of its trigger type, and
 * keeps each zone's fetch/computation isolated from the others.
 * <p>
 * Numeric precision of the calculation itself (bucket-weighting, non-zero durations, baseline zone/day handling) is
 * already covered by {@link CarbonImpactEndToEndTest}, {@link CarbonImpactCalculatorTest} and
 * {@link CarbonImpactBatchInvokerTest}; this test uses a plain no-op invoker (zero-duration windows) and focuses
 * purely on the multi-job/multi-zone wiring: does every job, of either type, in every zone, get its own published
 * result and its own history cleanup, from a single shared batch run.
 */
class CarbonImpactMultiZoneEndToEndTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    /**
     * Mirrors carbonintensity-api's currently PUBLIC zones (see that repo's {@code Zone} entity / {@code
     * Zone.Status}). green-scheduler has no dependency on that repo or its database - no shared enum, no DB access -
     * so this is a manually-maintained fixture rather than a live lookup; update it here if the supported set
     * changes. Read dynamically below (never inlined per job) so every entry automatically gets its own job without
     * the test needing to enumerate them by hand.
     */
    private static final List<String> SUPPORTED_ZONES = List.of("NL", "BE", "DE");

    private SimpleScheduler scheduler;

    @AfterEach
    void afterEach() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Test
    void everySupportedZoneGetsItsOwnResultAcrossBothTriggerTypes() throws Exception {
        FakeCarbonIntensityApi api = new FakeCarbonIntensityApi();
        CopyOnWriteArrayList<CarbonImpactResult> published = new CopyOnWriteArrayList<>();
        LocalDate day1 = LocalDate.of(2024, 6, 1);

        MutableClock mutableClock = new MutableClock(Clock.fixed(day1.atStartOfDay(UTC).plusHours(9).toInstant(), UTC));
        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(api);
        config.setClock(mutableClock);
        scheduler = new SimpleScheduler(config);
        scheduler.addJobListener(new Scheduler.EventListener() {
            @Override
            public void jobCarbonImpactCalculated(Trigger trigger, CarbonImpactResult result) {
                published.add(result);
            }
        });

        ScheduledInvoker noopInvoker = execution -> CompletableFuture.completedFuture(null);
        List<String> identities = SUPPORTED_ZONES.stream().map(zone -> "job-" + zone).toList();

        for (int i = 0; i < SUPPORTED_ZONES.size(); i++) {
            String zone = SUPPORTED_ZONES.get(i);
            String identity = identities.get(i);
            boolean useFixedWindow = i % 2 == 0; // alternate trigger type, so both are represented across the set

            AnnotationUtil.GreenScheduledBuilder builder = AnnotationUtil.newGreenScheduled()
                    .identity(identity)
                    .duration("10m")
                    .cron("0 0 20 * * ?") // naive-baseline hour, clear of both the job window and the batch window
                    .carbonIntensityZone(zone)
                    .timeZone("UTC");
            GreenScheduled greenScheduled = useFixedWindow
                    ? builder.fixedWindow("12:00 12:30").overdueGracePeriod("PT2H").build()
                    // a long minimum/maximum gap so this job doesn't become eligible to fire a second time before
                    // the test has shifted the clock all the way into the next day's batch window
                    : builder.successive("1h 20h 20h").build();
            GreenObserved greenObserved = AnnotationUtil.newGreenObserved().carbonImpact(true).build();

            scheduler.scheduleMethod(new ImmutableScheduledMethod(noopInvoker, "Test", identity,
                    List.of(greenScheduled), greenObserved));
        }

        mutableClock.getNotifier().register(scheduler);
        scheduler.start();

        // one shift covering both trigger types' earliest possible fire: successive fires within 1h of its own
        // creation (09:00-10:00), fixedWindow's own window is 12:00-12:30 - 13:00 is past both, and comfortably
        // inside the fixedWindow jobs' own 2h overdue grace period. No fake response is registered yet, so each
        // job's own *predicted*-side planning fetch (a separate mechanism from the batch's actual/baseline fetch
        // below) always misses and falls back to the bundled fallback dataset instead - keeping it out of the
        // per-zone/day request counts asserted further down.
        mutableClock.shift(Duration.ofHours(4));

        for (String identity : identities) {
            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(scheduler.getCarbonImpactHistory().windowsFor(identity)).hasSize(1));
        }

        for (String zone : SUPPORTED_ZONES) {
            api.respondWith(zone, day1, flatIntensity(zone, day1, 100));
            api.respondWith(zone, day1.plusDays(1), flatIntensity(zone, day1.plusDays(1), 100)); // defensive
        }

        // shift into the next day's default carbon-impact batch window (02:00-06:00 UTC), comfortably past the
        // widest possible jitter - the real CarbonImpactBatchTrigger fires once and processes every job above
        mutableClock.shift(Duration.ofHours(17));

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(published).hasSize(SUPPORTED_ZONES.size()));
        assertThat(published).extracting(CarbonImpactResult::day).containsOnly(day1);

        for (int i = 0; i < SUPPORTED_ZONES.size(); i++) {
            String zone = SUPPORTED_ZONES.get(i);
            String identity = identities.get(i);

            assertThat(scheduler.getCarbonImpactHistory().windowsFor(identity)).isEmpty();
            SimpleScheduler.SimpleTrigger trigger = (SimpleScheduler.SimpleTrigger) scheduler.getScheduledJob(identity);
            assertThat(trigger.getLastCarbonImpact()).isPresent();
            // 1 failed attempt from this job's own *predicted*-side planning fetch at schedule time (no fake
            // response is registered yet then, so it falls back to the bundled fallback dataset) + 1 successful
            // fetch from the batch itself (actual and naive-baseline both land on day1 in UTC here, so they share
            // one fetch) - exactly 2, neither more (which would mean cross-zone leakage) nor fewer (a skipped zone)
            assertThat(api.requestCount(zone, day1)).isEqualTo(2);
        }
    }

    private static CarbonIntensity flatIntensity(String zone, LocalDate date, int value) {
        CarbonIntensity intensity = new CarbonIntensity();
        intensity.setZone(zone); // this fake's response can be returned to the *predicted*-side planner too (its
                                 // window happens to fall on the same day as the actual/baseline fetch below), and
                                 // the planner's own cache keys on the response's zone same as CarbonIntensityFileApi
        intensity.setStart(date.atStartOfDay(UTC).toInstant());
        intensity.setEnd(date.plusDays(1).atStartOfDay(UTC).toInstant());
        intensity.setResolution(Duration.ofHours(1));
        intensity.setData(java.util.stream.IntStream.range(0, 24)
                .mapToObj(i -> java.math.BigDecimal.valueOf(value))
                .toList());
        return intensity;
    }
}
