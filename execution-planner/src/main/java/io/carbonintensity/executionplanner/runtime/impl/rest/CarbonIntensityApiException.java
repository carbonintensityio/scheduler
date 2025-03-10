package io.carbonintensity.executionplanner.runtime.impl.rest;

public class CarbonIntensityApiException extends RuntimeException {
    public CarbonIntensityApiException(Throwable e) {
        super(e);
    }

    public CarbonIntensityApiException(String message) {
        super(message);
    }
}
