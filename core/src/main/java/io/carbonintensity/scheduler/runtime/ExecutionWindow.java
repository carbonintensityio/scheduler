package io.carbonintensity.scheduler.runtime;

import java.time.Instant;
import java.util.Objects;

/**
 * The wall-clock start/end of one successful job execution, recorded for {@code carbonImpact}-enabled jobs so the
 * carbon-impact batch can later compute duration x intensity for it.
 */
record ExecutionWindow(Instant start, Instant end) {

    ExecutionWindow {
        Objects.requireNonNull(start, "Start cannot be null");
        Objects.requireNonNull(end, "End cannot be null");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End cannot be before start");
        }
    }

}
