package io.carbonintensity.executionplanner.runtime.impl;

public interface CarbonIntensityDataFetcher {

    CarbonIntensity fetchCarbonIntensity(ZonedCarbonIntensityPeriod zonedPeriod);
}
