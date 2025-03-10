package io.carbonintensity.scheduler;

public interface SkipPredicate {

    /**
     * @param execution
     * @return {@code true} if the given execution should be skipped, {@code false} otherwise
     */
    boolean test(ScheduledExecution execution);

    /**
     * Predicate that never skips execution.
     */
    class Never implements SkipPredicate {
        @Override
        public boolean test(ScheduledExecution execution) {
            return false;
        }
    }
}
