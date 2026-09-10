package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.carbonintensity.scheduler.observability.CarbonImpactHistoryStore;
import io.carbonintensity.scheduler.observability.ExecutionWindow;
import io.carbonintensity.scheduler.test.helper.DisabledDummyCarbonIntensityApi;

/**
 * {@link CarbonImpactHistoryStore} is an SPI, the same override-hook shape as {@code CarbonIntensityApi}: a consumer
 * can plug in their own implementation (e.g. backed by their own database, to survive a restart or outlive the
 * default backlog window) via {@link SchedulerConfig#setCarbonImpactHistoryStore}, instead of the bundled
 * {@link InMemoryCarbonImpactHistoryStore}. This proves the override hook is actually honored, not just present.
 */
class CarbonImpactHistoryStorePluggabilityTest {

    private SimpleScheduler scheduler;

    @AfterEach
    void afterEach() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Test
    void aCustomStoreRegisteredOnSchedulerConfigIsTheOneTheSchedulerActuallyUses() {
        CarbonImpactHistoryStore customStore = new RecordingCarbonImpactHistoryStore();

        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(new DisabledDummyCarbonIntensityApi());
        config.setCarbonImpactHistoryStore(customStore);
        scheduler = new SimpleScheduler(config);

        assertThat(scheduler.getCarbonImpactHistory()).isSameAs(customStore);
    }

    @Test
    void withoutAnOverrideTheDefaultInMemoryStoreIsUsed() {
        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonIntensityApi(new DisabledDummyCarbonIntensityApi());
        scheduler = new SimpleScheduler(config);

        assertThat(scheduler.getCarbonImpactHistory()).isInstanceOf(InMemoryCarbonImpactHistoryStore.class);
    }

    /**
     * A minimal, independent {@link CarbonImpactHistoryStore} implementation - stands in for a consumer's own
     * database-backed one, proving the SPI contract alone (not the bundled in-memory implementation) is enough to
     * plug in.
     */
    private static final class RecordingCarbonImpactHistoryStore implements CarbonImpactHistoryStore {

        private final Map<String, List<ExecutionWindow>> windowsByIdentity = new ConcurrentHashMap<>();

        @Override
        public void record(String identity, Instant start, Instant end) {
            windowsByIdentity.computeIfAbsent(identity, id -> new CopyOnWriteArrayList<>())
                    .add(new ExecutionWindow(start, end));
        }

        @Override
        public List<ExecutionWindow> windowsFor(String identity) {
            return List.copyOf(windowsByIdentity.getOrDefault(identity, List.of()));
        }

        @Override
        public void remove(String identity, List<ExecutionWindow> processed) {
            List<ExecutionWindow> windows = windowsByIdentity.get(identity);
            if (windows != null) {
                windows.removeAll(processed);
            }
        }
    }
}
