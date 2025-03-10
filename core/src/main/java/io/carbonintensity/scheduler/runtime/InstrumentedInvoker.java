package io.carbonintensity.scheduler.runtime;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.carbonintensity.scheduler.ScheduledExecution;
import io.carbonintensity.scheduler.spi.JobInstrumenter;
import io.carbonintensity.scheduler.spi.JobInstrumenter.JobInstrumentationContext;

/**
 * An {@link DelegateInvoker} implementation that adds instrumentation to job invocations.
 * <p>
 * This class wraps a {@link ScheduledInvoker} and delegates the job invocation to it,
 * while also applying instrumentation using a {@link JobInstrumenter}.
 * The instrumentation allows for tracking and monitoring of job executions,
 * providing metrics or logs for better observability.
 * </p>
 *
 *
 * @see JobInstrumenter
 * @see DelegateInvoker
 * @see ScheduledInvoker
 * @see ScheduledExecution
 */
public class InstrumentedInvoker extends DelegateInvoker {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedInvoker.class);

    private final JobInstrumenter instrumenter;

    public InstrumentedInvoker(ScheduledInvoker delegate, JobInstrumenter instrumenter) {
        super(delegate);
        this.instrumenter = instrumenter;
    }

    @Override
    public CompletionStage<Void> invoke(ScheduledExecution execution) throws Exception {
        log.trace("Running instrumented invoker for {} at {}.", execution.getTrigger().getId(),
                execution.getScheduledFireTime());
        return instrumenter.instrument(new JobInstrumentationContext() {

            @Override
            public CompletionStage<Void> executeJob() {
                return invokeDelegate(execution);
            }

            @Override
            public String getSpanName() {
                return execution.getTrigger().getId();
            }
        });
    }

}
