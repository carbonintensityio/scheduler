package io.carbonintensity.scheduler.quarkus.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.carbonintensity.executionplanner.runtime.impl.rest.CarbonIntensityApiConfig;
import io.carbonintensity.scheduler.ConcurrentExecution;
import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.ScheduledExecution;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.quarkus.factory.SimpleSchedulerFactory;
import io.carbonintensity.scheduler.runtime.ImmutableScheduledMethod;
import io.carbonintensity.scheduler.runtime.ScheduledInvoker;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;

import org.jboss.logging.Logger;
import org.jboss.threads.JBossScheduledThreadPoolExecutor;

import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;

import io.quarkus.runtime.StartupEvent;
import io.carbonintensity.scheduler.quarkus.DelayedExecution;
import io.carbonintensity.scheduler.quarkus.FailedExecution;
import io.carbonintensity.scheduler.quarkus.ScheduledJobPaused;
import io.carbonintensity.scheduler.quarkus.ScheduledJobResumed;

import io.carbonintensity.scheduler.quarkus.SchedulerPaused;
import io.carbonintensity.scheduler.quarkus.SchedulerResumed;
import io.carbonintensity.scheduler.quarkus.SkippedExecution;
import io.carbonintensity.scheduler.quarkus.SuccessfulExecution;
import io.carbonintensity.scheduler.quarkus.common.runtime.BaseScheduler;

import io.carbonintensity.scheduler.quarkus.common.runtime.Events;
import io.carbonintensity.scheduler.quarkus.common.runtime.ScheduledMethod;
import io.carbonintensity.scheduler.quarkus.common.runtime.SchedulerContext;
import io.carbonintensity.scheduler.quarkus.common.runtime.util.SchedulerUtils;
import io.carbonintensity.scheduler.quarkus.runtime.SchedulerRuntimeConfig.StartMode;
import io.carbonintensity.scheduler.quarkus.spi.JobInstrumenter;
import io.vertx.core.Vertx;


@Singleton
public class SimpleScheduler extends BaseScheduler  {

    private static final Logger LOG = Logger.getLogger(SimpleScheduler.class);

    // milliseconds
    public static final long CHECK_PERIOD = 1000L;

    private final ScheduledExecutorService scheduledExecutor;
    private volatile boolean running;
    private final ConcurrentMap<String, ScheduledTask> scheduledTasks;
    private final SchedulerConfig schedulerConfig;

    public SimpleScheduler(SchedulerContext context, SchedulerRuntimeConfig schedulerRuntimeConfig,
            Event<SkippedExecution> skippedExecutionEvent, Event<SuccessfulExecution> successExecutionEvent,
            Event<FailedExecution> failedExecutionEvent, Event<DelayedExecution> delayedExecutionEvent,
            Event<SchedulerPaused> schedulerPausedEvent, Event<SchedulerResumed> schedulerResumedEvent,
            Event<ScheduledJobPaused> scheduledJobPausedEvent,
            Event<ScheduledJobResumed> scheduledJobResumedEvent, Vertx vertx, SchedulerConfig schedulerConfig,
            Instance<JobInstrumenter> jobInstrumenter, ScheduledExecutorService blockingExecutor) {
        super(vertx,  schedulerRuntimeConfig.overdueGracePeriod(),
                new Events(skippedExecutionEvent, successExecutionEvent, failedExecutionEvent, delayedExecutionEvent,
                        schedulerPausedEvent, schedulerResumedEvent, scheduledJobPausedEvent, scheduledJobResumedEvent),
                jobInstrumenter, blockingExecutor);
        this.running = true;
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.schedulerConfig = schedulerConfig;

        var greenConfig = new io.carbonintensity.scheduler.runtime.SchedulerConfig();
        greenConfig.setEnabled(true);
        greenConfig.setStartMode(io.carbonintensity.scheduler.runtime.SchedulerConfig.StartMode.FORCED);
        greenConfig.setCarbonIntensityApiConfig(new CarbonIntensityApiConfig.Builder().build());
        var greenScheduler = new SimpleSchedulerFactory(Clock.systemDefaultZone()).createScheduler(greenConfig);
        greenScheduler.start();

        if (!schedulerRuntimeConfig.enabled()) {
            this.scheduledExecutor = null;
            LOG.info("Simple scheduler is disabled by config property and will not be started");
            return;
        }

        StartMode startMode = schedulerRuntimeConfig.startMode();
        LOG.info("======================================= sched methods = "+context.getScheduledMethods("Scheduled.SIMPLE").size());
        if (startMode == StartMode.NORMAL && context.getScheduledMethods("Scheduled.SIMPLE").isEmpty()
                && !context.forceSchedulerStart()) {
            this.scheduledExecutor = null;
            LOG.info("No scheduled business methods found - Simple scheduler will not be started");
            return;
        }

        ThreadFactory tf = new ThreadFactory() {

            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread t = new Thread(Thread.currentThread().getThreadGroup(), runnable,
                        "quarkus-scheduler-trigger-check-" + threadNumber.getAndIncrement(),
                        0);
                if (t.isDaemon()) {
                    t.setDaemon(false);
                }
                if (t.getPriority() != Thread.NORM_PRIORITY) {
                    t.setPriority(Thread.NORM_PRIORITY);
                }
                return t;
            }
        };
        // This executor is used to check all registered triggers every second
        this.scheduledExecutor = new JBossScheduledThreadPoolExecutor(1, tf, new Runnable() {
            @Override
            public void run() {
                // noop
            }
        });

        if (startMode == StartMode.HALTED) {
            running = false;
        }

        // Create triggers and invokers for @Scheduled methods
        for (ScheduledMethod method : context.getScheduledMethods("Scheduled.SIMPLE")) {
            LOG.info("!!#@ sched method ="+method.getMethodDescription());
            int nameSequence = 0;
            for (GreenScheduled scheduled : method.getSchedules()) {
                    JobInstrumenter instrumenter = null;
                    if (schedulerConfig.tracingEnabled() && jobInstrumenter.isResolvable()) {
                        instrumenter = jobInstrumenter.get();
                    }
                    io.carbonintensity.scheduler.runtime.ScheduledInvoker invoker = initInvoker(context.createInvoker(method.getInvokerClassName()), events,
                            ConcurrentExecution.PROCEED/* scheduled.concurrentExecution()*/, null/*initSkipPredicate(scheduled.skipExecutionIf())*/, instrumenter,
                            vertx, false, SchedulerUtils.parseExecutionMaxDelayAsMillis(scheduled), blockingExecutor);

                    greenScheduler.scheduleMethod(new ImmutableScheduledMethod(invoker, method.getDeclaringClassName(), method.getMethodName(), method.getSchedules()));



            }
        }
    }


    public boolean isStarted() {
        return scheduledExecutor != null;
    }


    public String implementation() {
        return "Scheduled.SIMPLE";
    }


    public Trigger unscheduleJob(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity);
        if (!identity.isEmpty()) {
            String parsedIdentity = SchedulerUtils.lookUpPropertyValue(identity);
            ScheduledTask task = scheduledTasks.get(parsedIdentity);
            if (task != null && task.isProgrammatic) {
                if (scheduledTasks.remove(task.trigger.id) != null) {
                    return task.trigger;
                }
            }
        }
        return null;
    }

    // Use Interceptor.Priority.PLATFORM_BEFORE to start the scheduler before regular StartupEvent observers
    void start(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent event) {
        if (scheduledExecutor == null) {
            return;
        }
        // Try to compute the initial delay to execute the checks near to the whole second
        // Note that this does not guarantee anything, it's just best effort
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trunc = now.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        scheduledExecutor.scheduleAtFixedRate(this::checkTriggers, ChronoUnit.MILLIS.between(now, trunc), CHECK_PERIOD,
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        try {
            if (scheduledExecutor != null) {
                scheduledExecutor.shutdownNow();
            }
        } catch (Exception e) {
            LOG.warn("Unable to shutdown the scheduler executor", e);
        }
    }

    void checkTriggers() {
        if (!running) {
            LOG.trace("Skip all triggers - scheduler paused");
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        LOG.tracef("Check triggers at %s", now);
        for (ScheduledTask task : scheduledTasks.values()) {
            task.execute(now, vertx);
        }
    }


    public void pause() {
        if (!isStarted()) {
            throw notStarted();
        }
        running = false;
        events.fireSchedulerPaused();
    }


    public void pause(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity, "Cannot pause - identity is null");
        if (identity.isEmpty()) {
            LOG.warn("Cannot pause - identity is empty");
            return;
        }
        String parsedIdentity = SchedulerUtils.lookUpPropertyValue(identity);
        ScheduledTask task = scheduledTasks.get(parsedIdentity);
        if (task != null) {
            task.trigger.setRunning(false);
            events.fireScheduledJobPaused(new ScheduledJobPaused(task.trigger));
        }
    }


    public boolean isPaused(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity);
        if (identity.isEmpty()) {
            return false;
        }
        String parsedIdentity = SchedulerUtils.lookUpPropertyValue(identity);
        ScheduledTask task = scheduledTasks.get(parsedIdentity);
        if (task != null) {
            return !task.trigger.isRunning();
        }
        return false;
    }


    public void resume() {
        if (!isStarted()) {
            throw notStarted();
        }
        running = true;
        events.fireSchedulerResumed();
    }


    public void resume(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity, "Cannot resume - identity is null");
        if (identity.isEmpty()) {
            LOG.warn("Cannot resume - identity is empty");
            return;
        }
        String parsedIdentity = SchedulerUtils.lookUpPropertyValue(identity);
        ScheduledTask task = scheduledTasks.get(parsedIdentity);
        if (task != null) {
            task.trigger.setRunning(true);
            events.fireScheduledJobResumed(new ScheduledJobResumed(task.trigger));
        }
    }


    public boolean isRunning() {
        return isStarted() && running;
    }


    public List<Trigger> getScheduledJobs() {
        if (!isStarted()) {
            throw notStarted();
        }
        return scheduledTasks.values().stream().map(task -> task.trigger).collect(Collectors.toUnmodifiableList());
    }


    public Trigger getScheduledJob(String identity) {
        if (!isStarted()) {
            throw notStarted();
        }
        Objects.requireNonNull(identity);
        if (identity.isEmpty()) {
            return null;
        }
        String parsedIdentity = SchedulerUtils.lookUpPropertyValue(identity);
        ScheduledTask task = scheduledTasks.get(parsedIdentity);
        if (task != null) {
            return task.trigger;
        }
        return null;
    }


    static class ScheduledTask {

        final boolean isProgrammatic;
        final SimpleTrigger trigger;
        final ScheduledInvoker invoker;

        ScheduledTask(SimpleTrigger trigger, ScheduledInvoker invoker, boolean isProgrammatic) {
            this.trigger = trigger;
            this.invoker = invoker;
            this.isProgrammatic = isProgrammatic;
        }

        void execute(ZonedDateTime now, Vertx vertx) {
            if (!trigger.isRunning()) {
                return;
            }
            ZonedDateTime scheduledFireTime = trigger.evaluate(now);
            if (scheduledFireTime != null) {
                try {
                    invoker.invoke(new SimpleScheduledExecution(now, scheduledFireTime, trigger));
                } catch (Throwable t) {
                    // already logged by the StatusEmitterInvoker
                }
            }
        }

    }

    static abstract class SimpleTrigger implements Trigger {

        protected final String id;
        protected final String methodDescription;
        private volatile boolean running;
        protected final ZonedDateTime start;
        protected volatile ZonedDateTime lastFireTime;

        SimpleTrigger(String id, ZonedDateTime start, String description) {
            this.id = id;
            this.start = start;
            this.running = true;
            this.methodDescription = description;
        }

        /**
         * @param now The current date-time in the default time zone
         * @return the scheduled time if fired, {@code null} otherwise
         */
        abstract ZonedDateTime evaluate(ZonedDateTime now);

        @Override
        public Instant getPreviousFireTime() {
            ZonedDateTime last = lastFireTime;
            return last != null ? last.toInstant() : null;
        }

        public String getId() {
            return id;
        }

        synchronized boolean isRunning() {
            return running;
        }

        synchronized void setRunning(boolean running) {
            this.running = running;
        }

        public String getMethodDescription() {
            return methodDescription;
        }

    }

    static class IntervalTrigger extends SimpleTrigger {

        // milliseconds
        private final long interval;
        private final Duration gracePeriod;

        IntervalTrigger(String id, ZonedDateTime start, long interval, Duration gracePeriod, String description) {
            super(id, start, description);
            this.interval = interval;
            this.gracePeriod = gracePeriod;
            if (interval < CHECK_PERIOD) {
                LOG.warnf(
                        "An every() value less than %s ms is not supported - the scheduled job will be executed with a delay: %s",
                        CHECK_PERIOD, description);
            }
        }

        @Override
        ZonedDateTime evaluate(ZonedDateTime now) {
            if (now.isBefore(start)) {
                return null;
            }
            if (lastFireTime == null) {
                // First execution
                lastFireTime = now.truncatedTo(ChronoUnit.SECONDS);
                return now;
            }
            long diff = ChronoUnit.MILLIS.between(lastFireTime, now);
            if (diff >= interval) {
                ZonedDateTime scheduledFireTime = lastFireTime.plus(Duration.ofMillis(interval));
                lastFireTime = now.truncatedTo(ChronoUnit.SECONDS);
                LOG.tracef("%s fired, diff=%s ms", this, diff);
                return scheduledFireTime;
            }
            return null;
        }

        @Override
        public Instant getNextFireTime() {
            ZonedDateTime last = lastFireTime;
            if (last == null) {
                last = start;
            }
            return last.plus(Duration.ofMillis(interval)).toInstant();
        }

        @Override
        public boolean isOverdue() {
            ZonedDateTime now = ZonedDateTime.now();
            if (now.isBefore(start)) {
                return false;
            }
            return lastFireTime == null || lastFireTime.plus(Duration.ofMillis(interval))
                    .plus(gracePeriod).isBefore(now);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("IntervalTrigger [id=").append(getId()).append(", interval=").append(interval).append("]");
            return builder.toString();
        }

    }

    static class CronTrigger extends SimpleTrigger {

        private final Cron cron;
        private final ExecutionTime executionTime;
        private final Duration gracePeriod;
        private final ZoneId timeZone;

        CronTrigger(String id, ZonedDateTime start, Cron cron, Duration gracePeriod, ZoneId timeZone, String description) {
            super(id, start, description);
            this.cron = cron;
            this.executionTime = ExecutionTime.forCron(cron);
            this.gracePeriod = gracePeriod;
            this.timeZone = timeZone;
            // The last fire time stores the zoned time
            this.lastFireTime = zoned(start);
        }

        @Override
        public Instant getNextFireTime() {
            return executionTime.nextExecution(lastFireTime).map(ZonedDateTime::toInstant).orElse(null);
        }

        @Override
        ZonedDateTime evaluate(ZonedDateTime now) {
            if (now.isBefore(start)) {
                return null;
            }
            now = zoned(now);
            Optional<ZonedDateTime> lastExecution = executionTime.lastExecution(now);
            if (lastExecution.isPresent()) {
                ZonedDateTime lastTruncated = lastExecution.get().truncatedTo(ChronoUnit.SECONDS);
                if (now.isAfter(lastTruncated) && lastFireTime.isBefore(lastTruncated)) {
                    LOG.tracef("%s fired, last=%s", this, lastTruncated);
                    lastFireTime = now;
                    return lastTruncated;
                }
            }
            return null;
        }

        @Override
        public boolean isOverdue() {
            ZonedDateTime now = ZonedDateTime.now();
            if (now.isBefore(start)) {
                return false;
            }
            now = zoned(now);
            Optional<ZonedDateTime> nextFireTime = executionTime.nextExecution(lastFireTime);
            return nextFireTime.isEmpty() || nextFireTime.get().plus(gracePeriod).isBefore(now);
        }

        @Override
        public String toString() {
            return "CronTrigger [id=" + id + ", cron=" + cron.asString() + ", gracePeriod=" + gracePeriod + ", timeZone="
                    + timeZone + "]";
        }

        private ZonedDateTime zoned(ZonedDateTime time) {
            return timeZone == null ? time : time.withZoneSameInstant(timeZone);
        }

    }

    static class SimpleScheduledExecution implements ScheduledExecution {

        private final ZonedDateTime fireTime;
        private final ZonedDateTime scheduledFireTime;
        private final Trigger trigger;

        public SimpleScheduledExecution(ZonedDateTime fireTime, ZonedDateTime scheduledFireTime, SimpleTrigger trigger) {
            this.fireTime = fireTime;
            this.scheduledFireTime = scheduledFireTime;
            this.trigger = trigger;
        }

        @Override
        public Trigger getTrigger() {
            return trigger;
        }

        @Override
        public Instant getFireTime() {
            return fireTime.toInstant();
        }

        @Override
        public Instant getScheduledFireTime() {
            return scheduledFireTime.toInstant();
        }

    }

}
