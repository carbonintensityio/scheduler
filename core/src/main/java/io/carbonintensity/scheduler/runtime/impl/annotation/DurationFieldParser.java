package io.carbonintensity.scheduler.runtime.impl.annotation;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Utility class for parsing duration values from string representations.
 */
class DurationFieldParser {

    private DurationFieldParser() {
    }

    static Duration parseDuration(String value) {
        if (Character.isDigit(value.charAt(0))) {
            if (Character.toLowerCase(value.charAt(value.length() - 1)) == 'd') {
                value = "P" + value;
            } else {
                value = "PT" + value;
            }
        }

        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            // This could only happen for config-based expressions
            throw new IllegalArgumentException(String.format(
                    "Invalid duration format: %s. Expected ISO-8601 format (e.g., 'PT15M', 'P1DT2H') or simplified ('15m', '1h').",
                    value), e);
        }
    }
}