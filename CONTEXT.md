# Green Scheduler

A Java library (core + Quarkus/Spring Boot extensions) that shifts scheduled jobs to run at carbon-optimal moments.

## Language

**GreenScheduled**:
A method-level annotation identifying a scheduled job whose execution moment is chosen by the carbon-aware planner, based on a required carbon-intensity zone.
_Avoid_: Green job, carbon-scheduled method

**GreenObserved**:
A method-level annotation, valid only alongside `GreenScheduled` on the same method, that opts a job into observability data: primarily fire-time/status transparency (last/next fire time, "is this a black box"), secondarily carbon-impact/savings metrics. Absence means no data is collected for that job.
_Avoid_: ScheduleInsights, Metrics annotation, carbon annotation, Observed (collides with Micrometer's own `@Observed`)
