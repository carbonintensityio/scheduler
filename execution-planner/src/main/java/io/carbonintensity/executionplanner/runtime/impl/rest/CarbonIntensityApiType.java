package io.carbonintensity.executionplanner.runtime.impl.rest;

public enum CarbonIntensityApiType {
    ACTUAL("actual"),
    PREDICTED("predicted");

    private final String apiPath;

    CarbonIntensityApiType(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getApiPath() {
        return apiPath;
    }
}
