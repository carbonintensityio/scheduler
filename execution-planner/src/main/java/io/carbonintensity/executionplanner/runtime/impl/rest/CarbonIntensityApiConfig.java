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

    public final String getApiKey() {
        return apiKey;
    }

    public final String getApiUrl() {
        return apiUrl;
    }

    public final boolean isEnabled() {
        return this.enabled;
    }

    public static class Builder {
        private String apiKey;
        private String apiUrl;

        public final Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public final Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public final CarbonIntensityApiConfig build() {
            return new CarbonIntensityApiConfig(this);
        }
    }
}
