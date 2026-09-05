package io.carbonintensity.scheduler.runtime;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.ZonedCarbonIntensityPeriod;
import io.carbonintensity.executionplanner.spi.CarbonIntensityApi;
import io.carbonintensity.scheduler.ScheduledExecution;
import io.carbonintensity.scheduler.observability.CarbonImpactResult;

/**
 * The invoker behind {@code CarbonImpactBatchTrigger}: once fired, computes and publishes carbon-impact/savings
 * figures for every {@code carbonImpact}-enabled job's pending {@link CarbonImpactHistory} windows.
 * <p>
 * Per job, pending windows are grouped by the calendar day they fall on (in the scheduler's own clock zone) and
 * processed one day at a time:
 * <ul>
 * <li>today (or later - shouldn't happen, but guarded) is always skipped, per CIIO-278: the upstream actual-
 * intensity data is never trustworthy same-day.</li>
 * <li>a day older than the configured backlog window is abandoned (its windows are dropped, never retried) and
 * logged as a warning - the cumulative savings total is a lower bound, not a reconciled ledger.</li>
 * <li>every other day is processed: fetch that zone/day's actual intensity, plus - per window - whatever zone/day
 * its own naive baseline (via {@link SimpleScheduler.SimpleTrigger#naiveBaselineFireTime}) falls on, since a
 * baseline can land on a different calendar day (and, for {@code FixedWindowTrigger}, a different zone) than the
 * actual execution. Every distinct (zone, day, dayZone) needed is fetched at most once, shared across every job in
 * the same zone via {@code intensityCache}, with retry-with-backoff on failure. Each window's actual and baseline
 * impact are summed into one {@link CarbonImpactResult}, published, and only then are those windows removed from
 * history - so a failure leaves them in place to retry on a later run instead of silently losing them.</li>
 * </ul>
 */
final class CarbonImpactBatchInvoker implements ScheduledInvoker {

    private static final Logger log = LoggerFactory.getLogger(CarbonImpactBatchInvoker.class);

    private final SimpleScheduler scheduler;
    private final CarbonImpactHistory history;
    private final Events events;
    private final Clock clock;
    private final CarbonIntensityApi actualApi;
    private final List<Duration> retryBackoffs;
    private final int backlogWindowDays;
    private final ScheduledExecutorService retryExecutor;

    /**
     * @param retryExecutor drives the delay between retries - owned and shut down by {@link SimpleScheduler},
     *        overridable in tests so they don't have to wait on real backoff delays
     */
    CarbonImpactBatchInvoker(SimpleScheduler scheduler, CarbonImpactHistory history, Events events, Clock clock,
            CarbonIntensityApi actualApi, List<Duration> retryBackoffs, int backlogWindowDays,
            ScheduledExecutorService retryExecutor) {
        this.scheduler = scheduler;
        this.history = history;
        this.events = events;
        this.clock = clock;
        this.actualApi = actualApi;
        this.retryBackoffs = retryBackoffs;
        this.backlogWindowDays = backlogWindowDays;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public CompletionStage<Void> invoke(ScheduledExecution execution) {
        ZoneId zoneId = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate oldestAllowedDay = today.minusDays((long) backlogWindowDays + 1);
        Map<String, CompletableFuture<CarbonIntensity>> intensityCache = new HashMap<>();

        List<CompletableFuture<Void>> perJob = new ArrayList<>();
        for (SimpleScheduler.SimpleTrigger trigger : scheduler.getCarbonImpactEnabledTriggers()) {
            perJob.add(processJob(trigger, zoneId, today, oldestAllowedDay, intensityCache));
        }
        return CompletableFuture.allOf(perJob.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> processJob(SimpleScheduler.SimpleTrigger trigger, ZoneId zoneId, LocalDate today,
            LocalDate oldestAllowedDay, Map<String, CompletableFuture<CarbonIntensity>> intensityCache) {
        String identity = trigger.getId();
        List<ExecutionWindow> windows = history.windowsFor(identity);
        if (windows.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Map<LocalDate, List<ExecutionWindow>> windowsByDay = new HashMap<>();
        for (ExecutionWindow window : windows) {
            LocalDate day = window.start().atZone(zoneId).toLocalDate();
            windowsByDay.computeIfAbsent(day, d -> new ArrayList<>()).add(window);
        }

        List<CompletableFuture<Void>> perDay = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ExecutionWindow>> entry : windowsByDay.entrySet()) {
            LocalDate day = entry.getKey();
            List<ExecutionWindow> dayWindows = entry.getValue();

            if (!day.isBefore(today)) {
                continue; // never today or later - CIIO-278
            }
            if (day.isBefore(oldestAllowedDay)) {
                log.warn(
                        "Abandoning carbon-impact data for job '{}' on {}: older than the {}-day backlog window, no longer retrying",
                        identity, day, backlogWindowDays);
                history.remove(identity, dayWindows);
                continue;
            }
            perDay.add(processDay(trigger, identity, zoneId, day, dayWindows, intensityCache));
        }
        return CompletableFuture.allOf(perDay.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> processDay(SimpleScheduler.SimpleTrigger trigger, String identity, ZoneId zoneId,
            LocalDate day, List<ExecutionWindow> windows, Map<String, CompletableFuture<CarbonIntensity>> intensityCache) {
        String zone = trigger.getCarbonIntensityZone();

        // Each window's naive baseline may land on a different calendar day - and, for FixedWindowTrigger, a
        // different zone - than the actual execution: the baseline is computed in the job's own configured
        // timeZone(), which need not match zoneId (the scheduler's own clock zone), and even a same-zone trigger can
        // round to a baseline just across a midnight boundary. Every distinct (zone, day, dayZone) actually needed
        // is fetched, not just `day` itself.
        Map<ExecutionWindow, ZonedDateTime> baselineFireTimes = new HashMap<>();
        Map<String, CompletableFuture<CarbonIntensity>> neededFetches = new HashMap<>();
        String actualKey = fetchCached(intensityCache, neededFetches, zone, zoneId, day);
        for (ExecutionWindow window : windows) {
            ZonedDateTime baselineFireTime = trigger.naiveBaselineFireTime(window.start().atZone(ZoneId.of("UTC")));
            baselineFireTimes.put(window, baselineFireTime);
            fetchCached(intensityCache, neededFetches, zone, baselineFireTime.getZone(), baselineFireTime.toLocalDate());
        }

        return CompletableFuture.allOf(neededFetches.values().toArray(CompletableFuture[]::new))
                .handle((ignored, failure) -> {
                    if (failure != null) {
                        log.warn("Failed to fetch actual carbon intensity for job '{}' on {} after retries - will be "
                                + "retried on a later run: {}", identity, day, failure.toString());
                        return null;
                    }
                    try {
                        CarbonImpactResult result = computeResult(trigger, day, windows, baselineFireTimes, zone,
                                actualKey, neededFetches);
                        trigger.setLastCarbonImpact(result);
                        events.fireJobCarbonImpactCalculated(trigger, result);
                        history.remove(identity, windows);
                    } catch (RuntimeException e) {
                        log.warn("Failed to compute carbon-impact for job '{}' on {} - will be retried on a later run",
                                identity, day, e);
                    }
                    return null;
                }).thenApply(ignored -> (Void) null);
    }

    /**
     * Registers a fetch for {@code (zone, day, dayZone)} in both {@code neededFetches} (this {@link #processDay}
     * call) and {@code intensityCache} (the whole {@link #invoke} run - so it's still shared across every job/day
     * sharing the same zone/day/zone triple), reusing either's existing future instead of fetching twice.
     *
     * @return the cache key the fetch was registered under
     */
    private String fetchCached(Map<String, CompletableFuture<CarbonIntensity>> intensityCache,
            Map<String, CompletableFuture<CarbonIntensity>> neededFetches, String zone, ZoneId dayZone, LocalDate day) {
        String key = cacheKeyFor(zone, day, dayZone);
        neededFetches.put(key, intensityCache.computeIfAbsent(key, k -> fetchWithRetry(zone, dayZone, day, 0)));
        return key;
    }

    private static String cacheKeyFor(String zone, LocalDate day, ZoneId dayZone) {
        return zone + "|" + day + "|" + dayZone.getId();
    }

    private CarbonImpactResult computeResult(SimpleScheduler.SimpleTrigger trigger, LocalDate day,
            List<ExecutionWindow> windows, Map<ExecutionWindow, ZonedDateTime> baselineFireTimes, String zone,
            String actualKey, Map<String, CompletableFuture<CarbonIntensity>> fetches) {
        BigDecimal totalActualImpact = BigDecimal.ZERO;
        BigDecimal totalBaselineImpact = BigDecimal.ZERO;
        CarbonIntensity actualIntensity = fetches.get(actualKey).join();

        for (ExecutionWindow window : windows) {
            totalActualImpact = totalActualImpact
                    .add(CarbonImpactCalculator.weightedImpact(actualIntensity, window.start(), window.end()));

            ZonedDateTime baselineFireTime = baselineFireTimes.get(window);
            Duration windowDuration = Duration.between(window.start(), window.end());
            Instant baselineStart = baselineFireTime.toInstant();
            Instant baselineEnd = baselineStart.plus(windowDuration);
            String baselineKey = cacheKeyFor(zone, baselineFireTime.toLocalDate(), baselineFireTime.getZone());
            CarbonIntensity baselineIntensity = fetches.get(baselineKey).join();
            totalBaselineImpact = totalBaselineImpact
                    .add(CarbonImpactCalculator.weightedImpact(baselineIntensity, baselineStart, baselineEnd));
        }

        BigDecimal savings = totalBaselineImpact.subtract(totalActualImpact);
        return new CarbonImpactResult(day, totalActualImpact.doubleValue(), savings.doubleValue(), clock.instant());
    }

    private CompletableFuture<CarbonIntensity> fetchWithRetry(String zone, ZoneId zoneId, LocalDate day, int attempt) {
        ZonedCarbonIntensityPeriod period = new ZonedCarbonIntensityPeriod.Builder()
                .withCarbonIntensityZone(zone)
                .withStartTime(day.atStartOfDay(zoneId))
                .withEndTime(day.plusDays(1).atStartOfDay(zoneId))
                .build();

        return actualApi.getCarbonIntensity(period).toCompletableFuture()
                .handle((intensity, failure) -> {
                    if (failure == null) {
                        return CompletableFuture.completedFuture(intensity);
                    }
                    if (attempt >= retryBackoffs.size()) {
                        CompletableFuture<CarbonIntensity> failed = new CompletableFuture<>();
                        failed.completeExceptionally(failure);
                        return failed;
                    }
                    Duration backoff = retryBackoffs.get(attempt);
                    log.debug("Retrying actual carbon-intensity fetch for zone '{}' on {} after {} (attempt {}/{})",
                            zone, day, backoff, attempt + 1, retryBackoffs.size());
                    CompletableFuture<CarbonIntensity> delayed = new CompletableFuture<>();
                    Executor delayedExecutor = CompletableFuture.delayedExecutor(backoff.toMillis(),
                            TimeUnit.MILLISECONDS, retryExecutor);
                    delayedExecutor.execute(() -> fetchWithRetry(zone, zoneId, day, attempt + 1)
                            .whenComplete((retriedIntensity, retriedFailure) -> {
                                if (retriedFailure != null) {
                                    delayed.completeExceptionally(retriedFailure);
                                } else {
                                    delayed.complete(retriedIntensity);
                                }
                            }));
                    return delayed;
                })
                .thenCompose(future -> future);
    }

}
