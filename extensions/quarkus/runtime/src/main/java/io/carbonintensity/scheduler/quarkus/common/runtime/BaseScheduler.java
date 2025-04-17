package io.carbonintensity.scheduler.quarkus.common.runtime;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.ScheduledExecutorService;

import io.carbonintensity.scheduler.ConcurrentExecution;
import io.carbonintensity.scheduler.SkipPredicate;
import io.carbonintensity.scheduler.runtime.ScheduledInvoker;
import jakarta.enterprise.inject.Instance;

import io.carbonintensity.scheduler.quarkus.spi.JobInstrumenter;
import io.vertx.core.Vertx;

public class BaseScheduler {

    protected final Vertx vertx;

    protected final Duration defaultOverdueGracePeriod;
    protected final Events events;
    protected final Instance<JobInstrumenter> jobInstrumenter;
    protected final ScheduledExecutorService blockingExecutor;

    public BaseScheduler(Vertx vertx,
            Duration defaultOverdueGracePeriod, Events events, Instance<JobInstrumenter> jobInstrumenter,
            ScheduledExecutorService blockingExecutor) {
        this.vertx = vertx;

        this.defaultOverdueGracePeriod = defaultOverdueGracePeriod;
        this.events = events;
        this.jobInstrumenter = jobInstrumenter;
        this.blockingExecutor = blockingExecutor;
    }

    protected UnsupportedOperationException notStarted() {
        return new UnsupportedOperationException("Scheduler was not started");
    }

    protected io.carbonintensity.scheduler.runtime.ScheduledInvoker initInvoker(ScheduledInvoker invoker, Events events,
                                                                                ConcurrentExecution concurrentExecution, SkipPredicate skipPredicate, JobInstrumenter instrumenter,
                                                                                Vertx vertx, boolean skipOffloadingInvoker,
                                                                                OptionalLong delay, ScheduledExecutorService blockingExecutor) {
//        invoker = new StatusEmitterInvoker(invoker, events.successExecution, events.failedExecution);
//        if (concurrentExecution == ConcurrentExecution.SKIP) {
//            invoker = new SkipConcurrentExecutionInvoker(invoker, events.skippedExecution);
//        }
////        if (skipPredicate != null) {
////            invoker = new SkipPredicateInvoker(invoker, skipPredicate, events.skippedExecution);
////        }
//        if (instrumenter != null) {
//            invoker = new InstrumentedInvoker(invoker, instrumenter);
//        }
////        if (!skipOffloadingInvoker) {
////            invoker = new OffloadingInvoker(invoker, vertx);
////        }
//        if (delay.isPresent()) {
//            invoker = new DelayedExecutionInvoker(invoker, delay.getAsLong(), blockingExecutor, events.delayedExecution);
//        }
        return invoker;
    }

//    protected Scheduled.SkipPredicate initSkipPredicate(Class<? extends SkipPredicate> predicateClass) {
//        if (predicateClass.equals(Scheduled.Never.class)) {
//            return null;
//        }
//        return SchedulerUtils.instantiateBeanOrClass(predicateClass);
//    }

}
