package io.carbonintensity.scheduler.runtime;

public class SimpleSchedulerNotifier {
    SimpleScheduler scheduler;

    public void register(SimpleScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void nofity() {
        if (scheduler != null) {
            scheduler.checkTriggers();
        }
    }
}
