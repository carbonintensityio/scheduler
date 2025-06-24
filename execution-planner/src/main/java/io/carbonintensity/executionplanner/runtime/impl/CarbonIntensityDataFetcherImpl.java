package io.carbonintensity.executionplanner.runtime.impl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.executionplanner.spi.CarbonIntensityApi;

public class CarbonIntensityDataFetcherImpl implements CarbonIntensityDataFetcher {

    private static final Logger logger = LoggerFactory.getLogger(CarbonIntensityDataFetcherImpl.class);
    private final CarbonIntensityCache cache = new CarbonIntensityCache();
    private final CarbonIntensityApi restApi;
    private final CarbonIntensityApi fallbackApi;

    public CarbonIntensityDataFetcherImpl(CarbonIntensityApi restApi, CarbonIntensityApi fallbackApi) {
        this.fallbackApi = fallbackApi;
        if (restApi.isEnabled()) {
            this.restApi = restApi;
        } else {
            logger.warn("Rest API not configured. Using only fallback API.");
            this.restApi = this.fallbackApi;
        }
    }

    public CarbonIntensity fetchCarbonIntensity(ZonedCarbonIntensityPeriod zonedPeriod) {
        logger.trace("Fetching data for zone {}", zonedPeriod);
        var carbonIntensity = getFromCache(zonedPeriod);
        if (carbonIntensity.isPresent()) {
            logger.trace("Found carbonIntensity data in cache");
            return carbonIntensity.get();
        }

        logger.debug("Empty cache, fetching data from rest API {}", zonedPeriod);
        var restResponse = restApi.getCarbonIntensity(zonedPeriod)
                .exceptionally(e -> handleException(e, zonedPeriod))
                .join();

        return storeInCache(restResponse);
    }

    private Optional<CarbonIntensity> getFromCache(ZonedCarbonIntensityPeriod zonedPeriod) {
        var start = zonedPeriod.getStartTime().toInstant();
        return cache.get(new CarbonIntensityCache.Key(start, zonedPeriod.getZone()));
    }

    private CarbonIntensity storeInCache(CarbonIntensity carbonIntensity) {
        var start = carbonIntensity.getStart();
        return cache.put(new CarbonIntensityCache.Key(start, carbonIntensity.getZone()), carbonIntensity);
    }

    private CarbonIntensity handleException(Throwable e, ZonedCarbonIntensityPeriod zonedPeriod) {
        logger.error("Failed to get data from rest API. Using fallback API", e);
        return fallbackApi.getCarbonIntensity(zonedPeriod).join();
    }
}
