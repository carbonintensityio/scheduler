package io.carbonintensity.scheduler.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.execannotations.ExecutionModelAnnotationsAllowedBuildItem;

public class SchedulerMethodsProcessor {
    @BuildStep
    ExecutionModelAnnotationsAllowedBuildItem schedulerMethods() {
        return new ExecutionModelAnnotationsAllowedBuildItem(
                method -> method.hasDeclaredAnnotation(SchedulerDotNames.SCHEDULED_NAME)
                        || method.hasDeclaredAnnotation(SchedulerDotNames.SCHEDULES_NAME));
    }
}
