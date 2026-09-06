package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.Scheduler;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.observability.DecisionReason;
import io.carbonintensity.scheduler.observability.DecisionStrategy;
import io.carbonintensity.scheduler.observability.DecisionTimelineEntry;
import io.carbonintensity.scheduler.observability.DecisionTimelineStore;
import io.carbonintensity.scheduler.test.helper.AnnotationUtil;
import io.carbonintensity.scheduler.test.helper.DisabledDummyCarbonIntensityApi;
import io.carbonintensity.scheduler.test.helper.MutableClock;

/**
 * Verifies the decision timeline is wired end to end through the real {@link SimpleScheduler} tick (not a manual
 * {@code recordDecision} call) - a real fire produces a correctly-populated {@link DecisionTimelineEntry} via both
 * the configured {@link DecisionTimelineStore} and the {@link Scheduler.EventListener#jobDecisionRecorded} event -
 * plus the two safety properties recording must uphold: a storage failure never blocks the job itself, and the MDC
 * identity never leaks across jobs sharing the same pooled executor thread.
 */
class DecisionTimelineWiringTest {

    private SimpleScheduler scheduler;

    @AfterEach
    void afterEach() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Test
    void aRealFixedWindowFireRecordsADecisionTimelineEntry() throws Exception {
        CopyOnWriteArrayList<DecisionTimelineEntry> storedEntries = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<DecisionTimelineEntry> firedEvents = new CopyOnWriteArrayList<>();
        DecisionTimelineStore recordingStore = new DecisionTimelineStore() {
            @Override
            public void record(String identity, DecisionTimelineEntry entry) {
                storedEntries.add(entry);
            }

            @Override
            public List<DecisionTimelineEntry> entriesFor(String identity) {
                return List.copyOf(storedEntries);
            }
        };

        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(new DisabledDummyCarbonIntensityApi());
        config.setDecisionTimelineStore(recordingStore);

        // Same fixture as TestFixedWindowScheduler: with the fallback file dataset, "05:15 08:15" consistently
        // resolves its greenest slot to 07:15 Europe/Amsterdam - shifting from 04:16 to 07:16 crosses it.
        MutableClock mutableClock = new MutableClock(Clock.fixed(
                ZonedDateTime.of(LocalDateTime.of(LocalDate.of(2024, 6, 1), LocalTime.of(4, 16)), ZoneId.of("Europe/Amsterdam"))
                        .toInstant(),
                ZoneId.of("UTC")));
        config.setClock(mutableClock);

        scheduler = new SimpleScheduler(config);
        scheduler.addJobListener(new Scheduler.EventListener() {
            @Override
            public void jobDecisionRecorded(Trigger trigger, DecisionTimelineEntry entry) {
                firedEvents.add(entry);
            }
        });

        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .fixedWindow("05:15 08:15")
                .carbonIntensityZone("NL")
                .duration("2h")
                .identity("test")
                .timeZone("Europe/Amsterdam")
                .build();
        scheduler.scheduleMethod(
                new ImmutableScheduledMethod(noopInvoker(), getClass().getName(), "test", List.of(greenScheduled)));
        mutableClock.getNotifier().register(scheduler);
        scheduler.start();

        mutableClock.shift(java.time.Duration.ofHours(3));

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(storedEntries).hasSize(1));

        DecisionTimelineEntry entry = storedEntries.get(0);
        assertThat(entry.strategy()).isEqualTo(DecisionStrategy.FIXED_WINDOW);
        assertThat(entry.reason()).isEqualTo(DecisionReason.GREENEST_AVAILABLE_SLOT);
        assertThat(entry.intensityValue()).isEmpty();
        assertThat(firedEvents).containsExactly(entry);
    }

    @Test
    void aStorageFailureIsCaughtAndDoesNotPreventTheJobFromRunning() throws Exception {
        DecisionTimelineStore throwingStore = new DecisionTimelineStore() {
            @Override
            public void record(String identity, DecisionTimelineEntry entry) {
                throw new RuntimeException("simulated storage failure");
            }

            @Override
            public List<DecisionTimelineEntry> entriesFor(String identity) {
                return List.of();
            }
        };

        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(new DisabledDummyCarbonIntensityApi());
        config.setDecisionTimelineStore(throwingStore);

        MutableClock mutableClock = new MutableClock(Clock.fixed(
                ZonedDateTime.of(LocalDateTime.of(LocalDate.of(2024, 6, 1), LocalTime.of(4, 16)), ZoneId.of("Europe/Amsterdam"))
                        .toInstant(),
                ZoneId.of("UTC")));
        config.setClock(mutableClock);

        CountDownLatch jobRan = new CountDownLatch(1);
        scheduler = new SimpleScheduler(config);
        GreenScheduled greenScheduled = AnnotationUtil.newGreenScheduled()
                .fixedWindow("05:15 08:15")
                .carbonIntensityZone("NL")
                .duration("2h")
                .identity("test")
                .timeZone("Europe/Amsterdam")
                .build();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(execution -> {
            jobRan.countDown();
            return CompletableFuture.completedFuture(null);
        }, getClass().getName(), "test", List.of(greenScheduled)));
        mutableClock.getNotifier().register(scheduler);
        scheduler.start();

        mutableClock.shift(java.time.Duration.ofHours(3));

        assertThat(jobRan.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void mdcIdentityDoesNotLeakAcrossJobsOnTheSamePooledThread() throws Exception {
        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(new DisabledDummyCarbonIntensityApi());
        config.setJobExecutors(1); // force both jobs below onto the exact same executor thread

        MutableClock mutableClock = new MutableClock(Clock.fixed(
                ZonedDateTime.of(LocalDateTime.of(LocalDate.of(2024, 6, 1), LocalTime.of(4, 16)), ZoneId.of("Europe/Amsterdam"))
                        .toInstant(),
                ZoneId.of("UTC")));
        config.setClock(mutableClock);
        scheduler = new SimpleScheduler(config);

        CopyOnWriteArrayList<String> observedIdentityA = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> observedIdentityB = new CopyOnWriteArrayList<>();
        CountDownLatch jobADone = new CountDownLatch(1);
        CountDownLatch jobBDone = new CountDownLatch(1);

        GreenScheduled jobA = AnnotationUtil.newGreenScheduled()
                .fixedWindow("05:15 08:15").carbonIntensityZone("NL").duration("2h")
                .identity("job-a").timeZone("Europe/Amsterdam").build();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(execution -> {
            observedIdentityA.add(MDC.get("identity"));
            jobADone.countDown();
            return CompletableFuture.completedFuture(null);
        }, getClass().getName(), "job-a", List.of(jobA)));

        mutableClock.getNotifier().register(scheduler);
        scheduler.start();
        mutableClock.shift(java.time.Duration.ofHours(3)); // fires job-a at 07:16
        assertThat(jobADone.await(2, TimeUnit.SECONDS)).isTrue();

        // a second, differently-identified job registered right after, targeting the same (still-open) window -
        // its greenest slot (deterministically the same 07:15, per the fallback dataset) is already in the past
        // relative to "now", so it fires as soon as triggers are checked again - still the same single-thread pool
        GreenScheduled jobB = AnnotationUtil.newGreenScheduled()
                .fixedWindow("05:15 08:15").carbonIntensityZone("NL").duration("2h")
                .identity("job-b").timeZone("Europe/Amsterdam").build();
        scheduler.scheduleMethod(new ImmutableScheduledMethod(execution -> {
            observedIdentityB.add(MDC.get("identity"));
            jobBDone.countDown();
            return CompletableFuture.completedFuture(null);
        }, getClass().getName(), "job-b", List.of(jobB)));

        mutableClock.shift(java.time.Duration.ZERO); // force a synchronous checkTriggers() at the current time
        assertThat(jobBDone.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(observedIdentityA).containsExactly("job-a");
        assertThat(observedIdentityB).containsExactly("job-b"); // not "job-a" - proves no leak onto the shared thread
    }

    @Test
    void simpleTriggerStrategyThrowsByDefaultUnlessOverridden() {
        SimpleScheduler.SimpleTrigger noStrategyTrigger = new SimpleScheduler.SimpleTrigger("no-strategy",
                Clock.systemUTC(), ZonedDateTime.now(), "test") {
            @Override
            ZonedDateTime evaluate(ZonedDateTime now) {
                return null;
            }

            @Override
            public Instant getNextFireTime() {
                return null;
            }

            @Override
            public boolean isOverdue() {
                return false;
            }
        };

        assertThatThrownBy(noStrategyTrigger::strategy).isInstanceOf(UnsupportedOperationException.class);
    }

    private ScheduledInvoker noopInvoker() {
        return execution -> CompletableFuture.completedFuture(null);
    }
}
