package io.carbonintensity.scheduler.test.helper;

import java.util.concurrent.CompletableFuture;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.ZonedCarbonIntensityPeriod;
import io.carbonintensity.executionplanner.spi.CarbonIntensityApi;

public class DisabledDummyCarbonIntensityApi implements CarbonIntensityApi {

    @Override
    public CompletableFuture<CarbonIntensity> getCarbonIntensity(ZonedCarbonIntensityPeriod zonedCarbonIntensityPeriod) {
        return null;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
