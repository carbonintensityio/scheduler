package io.carbonintensity.executionplanner.runtime.impl.rest;

public class CarbonIntensityApiConfig {
    private final String apiKey;
    private final String apiUrl;
    private final boolean enabled;

    protected CarbonIntensityApiConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiUrl = builder.apiUrl;
        this.enabled = this.apiKey != null && !this.apiKey.isBlank() && this.apiUrl != null
                && !this.apiUrl.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public static class Builder {
        private String apiKey;
        private String apiUrl;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public CarbonIntensityApiConfig build() {
            return new CarbonIntensityApiConfig(this);
        }
    }
}
