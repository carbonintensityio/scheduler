package io.carbonintensity.scheduler.runtime;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.ZonedCarbonIntensityPeriod;
import io.carbonintensity.executionplanner.spi.CarbonIntensityApi;

/**
 * A controllable {@link CarbonIntensityApi}: pre-programmed per zone/day responses, an injectable number of
 * simulated failures before succeeding, and a count of requests actually made per zone/day (to verify per-zone/day
 * request sharing and that no real outbound call is ever made in tests).
 */
final class FakeCarbonIntensityApi implements CarbonIntensityApi {

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
    public CompletableFuture<CarbonIntensity> getCarbonIntensity(ZonedCarbonIntensityPeriod period) {
        LocalDate date = period.getStartTime().toLocalDate();
        String key = key(period.getZone(), date);
        requestCounts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();

        AtomicInteger remaining = failuresRemaining.get(key);
        if (remaining != null && remaining.getAndUpdate(v -> Math.max(0, v - 1)) > 0) {
            return CompletableFuture.failedFuture(new RuntimeException("simulated failure"));
        }
        CarbonIntensity response = responses.get(key);
        if (response == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("no fake response configured for " + key));
        }
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private static String key(String zone, LocalDate date) {
        return zone + "|" + date;
    }
}
