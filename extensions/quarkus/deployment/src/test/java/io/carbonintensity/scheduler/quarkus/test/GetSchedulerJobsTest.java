package io.carbonintensity.scheduler.quarkus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.carbonintensity.scheduler.GreenScheduled;
import io.carbonintensity.scheduler.Trigger;
import io.carbonintensity.scheduler.runtime.SimpleScheduler;
import io.quarkus.test.QuarkusUnitTest;

public class GetSchedulerJobsTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(GetSchedulerJobsTest.Jobs.class));

    @Inject
    SimpleScheduler greenScheduler;

    @Test
    public void testSchedulerListScheduledJobsMethod() {
        List<Trigger> triggers = greenScheduler.getScheduledJobs();
        assertEquals(1, triggers.size());
        Trigger trigger = triggers.get(0);
        assertEquals("the_schedule", trigger.getId());
    }

    static class Jobs {
        @GreenScheduled(identity = "the_schedule", successive = "1S 4S 5S", duration = "PT30M", zone = "NL")
        void ping() {
        }
    }

}
