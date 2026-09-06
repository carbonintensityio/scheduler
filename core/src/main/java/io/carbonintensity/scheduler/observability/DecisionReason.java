package io.carbonintensity.scheduler.observability;

/**
 * Why a particular moment was chosen for a job's {@link DecisionTimelineEntry}, distinguishing a genuinely
 * carbon-aware choice from a fallback the job's {@link DecisionStrategy} took when no carbon-aware slot was
 * available. Kept to what the scheduler can actually distinguish today - not a speculative superset.
 */
public enum DecisionReason {

    /**
     * A carbon-aware planner found and chose the greenest available slot within the job's configured constraints.
     */
    GREENEST_AVAILABLE_SLOT("the greenest available slot was chosen"),

    /**
     * A {@code fixedWindow()} job found no green window and fell back to its configured fallback cron expression.
     */
    FALLBACK_TO_CONFIGURED_CRON("no green window was found - fell back to the configured cron schedule"),

    /**
     * A {@code successive()} job found no green slot and fell back to plain interval spacing (the average of its
     * minimum/maximum gap).
     */
    FALLBACK_TO_PLAIN_INTERVAL("no green slot was found - fell back to plain interval spacing");

    private final String label;

    DecisionReason(String label) {
        this.label = label;
    }

    /**
     * @return a short, human-readable explanation suitable for direct use in log messages or documentation examples
     */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

}
