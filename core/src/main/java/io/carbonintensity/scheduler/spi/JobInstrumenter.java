package io.carbonintensity.scheduler.spi;

import java.util.concurrent.CompletionStage;

/**
 * Instruments a scheduled job.
 */
public interface JobInstrumenter {

    CompletionStage<Void> instrument(JobInstrumentationContext context);

    interface JobInstrumentationContext {

        String getSpanName();

        CompletionStage<Void> executeJob();

    }
}
