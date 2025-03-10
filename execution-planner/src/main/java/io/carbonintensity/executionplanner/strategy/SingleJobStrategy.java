package io.carbonintensity.executionplanner.strategy;

import static io.carbonintensity.executionplanner.planner.Timeslot.getTimeslots;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.executionplanner.planner.Timeslot;
import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;

/**
 * Places a single job in the best spot in the given window.
 * Does not consider the next job's placement.
 */
public class SingleJobStrategy implements PlanningStrategy {

    private static final Logger log = LoggerFactory.getLogger(SingleJobStrategy.class);

    private final Duration resolution;

    /**
     * Default constructor, uses a resolution of 30 minutes to find timeslots
     */
    public SingleJobStrategy() {
        this(Duration.ofMinutes(30));
    }

    public SingleJobStrategy(Duration resolution) {
        this.resolution = resolution;
    }

    @Override
    public Timeslot bestTimeslot(ZonedDateTime ws, ZonedDateTime we, Duration duration, CarbonIntensity carbonIntensity) {

        // create timeslots and calculate carbon intensity
        List<Timeslot> timeslots = getTimeslots(ws, we, duration, resolution, carbonIntensity);

        if (timeslots.isEmpty()) {
            log.warn("No timeslots found!  {}", carbonIntensity.getData().size());
            return null;
        }

        Timeslot best = null;
        for (Timeslot t : timeslots) {
            if (best == null || t.carbonIntensity().compareTo(best.carbonIntensity()) < 0) {
                best = t;
            }
        }

        log.debug("Found best timeslot of {} job between {} - {} at {} (CI: {})", duration, ws, we, best.start(),
                best.carbonIntensity());
        return best;
    }

}
