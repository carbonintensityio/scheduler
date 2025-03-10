package io.carbonintensity.executionplanner.runtime.impl.rest;

import java.util.concurrent.CompletableFuture;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;
import io.carbonintensity.executionplanner.runtime.impl.ZonedCarbonIntensityPeriod;

/**
 * Interface for CarbonIntensity API.
 */
public interface CarbonIntensityApi {

    /**
     * Gets carbon intensity data for given period and zone
     *
     * @param zonedPeriod period and zone
     * @return data for given period
     */
    CompletableFuture<CarbonIntensity> getCarbonIntensity(ZonedCarbonIntensityPeriod zonedPeriod);

    boolean isEnabled();
}
