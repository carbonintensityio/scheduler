package io.carbonintensity.scheduler.runtime;

import java.util.List;

public interface SchedulerContext {

    List<ScheduledMethod> getScheduledMethods();

}
