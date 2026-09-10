# Green Scheduler

A Java library (core + Quarkus/Spring Boot extensions) that shifts scheduled jobs to run at carbon-optimal moments.

## Language

**GreenScheduled**:
A method-level annotation identifying a scheduled job whose execution moment is chosen by the carbon-aware planner, based on a required carbon-intensity zone.
_Avoid_: Green job, carbon-scheduled method

**GreenObserved**:
A method-level annotation, valid only alongside `GreenScheduled` on the same method, that opts a job into observability metrics export (Micrometer, per extension): primarily fire-time/status transparency (last/next fire time, "is this a black box"), secondarily carbon-impact/savings metrics. Does not change what core tracks internally - only whether an extension exports it as metrics. Absence means no metrics are exported for that job.
_Avoid_: ScheduleInsights, Metrics annotation, carbon annotation, Observed (collides with Micrometer's own `@Observed`)

**CarbonImpactBatchTrigger**:
The internal, Clock-driven trigger that once daily recomputes carbon-impact/savings for the previous day's `GreenObserved(carbonImpact=true)` executions, once upstream intensity data has settled. Not a wall-clock-sleeping thread — evaluated on each scheduler tick like any other trigger, for testability.
_Avoid_: daily batch job, metrics cron
