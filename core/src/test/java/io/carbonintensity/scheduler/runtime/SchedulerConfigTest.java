package io.carbonintensity.scheduler.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class SchedulerConfigTest {

    @Test
    void shouldUseDefaultCarbonImpactBatchSettings() {
        SchedulerConfig config = new SchedulerConfig();

        assertThat(config.getCarbonImpactBatchWindowStart())
                .isEqualTo(SchedulerDefaults.DEFAULT_CARBON_IMPACT_BATCH_WINDOW_START);
        assertThat(config.getCarbonImpactBatchWindowEnd()).isEqualTo(SchedulerDefaults.DEFAULT_CARBON_IMPACT_BATCH_WINDOW_END);
        assertThat(config.getCarbonImpactRetryBackoffs()).isEqualTo(SchedulerDefaults.DEFAULT_CARBON_IMPACT_RETRY_BACKOFFS);
        assertThat(config.getCarbonImpactBacklogWindowDays())
                .isEqualTo(SchedulerDefaults.DEFAULT_CARBON_IMPACT_BACKLOG_WINDOW_DAYS);
    }

    @Test
    void shouldAllowNarrowingTheCarbonImpactBatchWindow() {
        SchedulerConfig config = new SchedulerConfig();

        config.setCarbonImpactBatchWindow(LocalTime.of(3, 0), LocalTime.of(3, 30));

        assertThat(config.getCarbonImpactBatchWindowStart()).isEqualTo(LocalTime.of(3, 0));
        assertThat(config.getCarbonImpactBatchWindowEnd()).isEqualTo(LocalTime.of(3, 30));
    }

    @Test
    void shouldRejectCarbonImpactBatchWindowThatDoesNotStartBeforeItEnds() {
        SchedulerConfig config = new SchedulerConfig();

        assertThatThrownBy(() -> config.setCarbonImpactBatchWindow(LocalTime.of(6, 0), LocalTime.of(2, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spanning midnight is not supported");
        assertThatThrownBy(() -> config.setCarbonImpactBatchWindow(LocalTime.of(3, 0), LocalTime.of(3, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowEmptyRetryBackoffsToDisableRetrying() {
        SchedulerConfig config = new SchedulerConfig();

        config.setCarbonImpactRetryBackoffs(List.of());

        assertThat(config.getCarbonImpactRetryBackoffs()).isEmpty();
    }

    @Test
    void shouldRejectNegativeBacklogWindow() {
        SchedulerConfig config = new SchedulerConfig();

        assertThatThrownBy(() -> config.setCarbonImpactBacklogWindowDays(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowZeroBacklogWindowToDisableCatchUp() {
        SchedulerConfig config = new SchedulerConfig();

        config.setCarbonImpactBacklogWindowDays(0);

        assertThat(config.getCarbonImpactBacklogWindowDays()).isZero();
    }

    @Test
    void shouldRejectNullRetryBackoffs() {
        SchedulerConfig config = new SchedulerConfig();

        assertThatThrownBy(() -> config.setCarbonImpactRetryBackoffs(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void retryBackoffsListShouldBeImmutable() {
        SchedulerConfig config = new SchedulerConfig();
        config.setCarbonImpactRetryBackoffs(List.of(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> config.getCarbonImpactRetryBackoffs().add(Duration.ofSeconds(2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
