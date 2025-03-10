package io.carbonintensity.scheduler.runtime.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityJsonParser;
import io.carbonintensity.executionplanner.strategy.SingleJobStrategy;

@State(Scope.Benchmark)
public class TestPerformance { //NOSONAR should be public for @state

    private static final double MAX_DEVIATION = 0.10; // 10 % deviation allowed
    //    private static final double REFERENCE_SCORE = 8.704; // arjanl's macbook pro, original score for 24h window
    private static final double REFERENCE_SCORE = 18073.666; // arjanl's macbook pro, window granularity of 30 mins score for 24h window
    private static final DecimalFormat DF = new DecimalFormat("0.000");

    private CarbonIntensity carbonIntensity;

    @Setup(Level.Trial)
    public void setUp() {
        CarbonIntensityJsonParser parser = new CarbonIntensityJsonParser();
        carbonIntensity = parser.parse(
                ClassLoader.getSystemResourceAsStream("day-ahead-20240824-Z.json"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkScheduler() {
        SingleJobStrategy singleJobStrategy = new SingleJobStrategy();
        ZonedDateTime ws = ZonedDateTime.parse("2024-08-27T00:00:00Z");
        ZonedDateTime we = ZonedDateTime.parse("2024-08-28T00:00:00Z");
        Duration d = Duration.ofSeconds(60);
        singleJobStrategy.bestTimeslot(ws, we, d, carbonIntensity);
    }

    @Test
    @Disabled
    void runPerformanceBenchmarks() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TestPerformance.class.getSimpleName())
                .build();
        Collection<RunResult> runResults = new Runner(opt).run();
        assertFalse(runResults.isEmpty());
        for (RunResult runResult : runResults) {
            assertDeviationWithin(runResult, REFERENCE_SCORE, MAX_DEVIATION);
        }
    }

    private static void assertDeviationWithin(RunResult result, double referenceScore, double maxDeviation) {
        double score = result.getPrimaryResult().getScore();
        double deviation = Math.abs(score / referenceScore - 1);
        String deviationString = DF.format(deviation * 100) + "%";
        String maxDeviationString = DF.format(maxDeviation * 100) + "%";
        String errorMessage = "Deviation " + deviationString + " exceeds maximum allowed deviation " + maxDeviationString;
        assertTrue(deviation < maxDeviation, errorMessage);
    }
}
