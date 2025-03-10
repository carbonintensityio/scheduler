package io.carbonintensity.scheduler.spring.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.carbonintensity.scheduler.ScheduledExecution;

@ExtendWith(MockitoExtension.class)
class MethodScheduledInvokerTests {

    @Mock
    ScheduledExecution scheduledExecution;

    @Mock
    TestBean bean;

    Method runMethod;
    MethodScheduledInvoker invoker;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        runMethod = TestBean.class.getMethod("run");
        invoker = new MethodScheduledInvoker(bean, runMethod);
    }

    @Test
    void givenMethod_whenInvoked_thenCompleteStage() {
        doNothing().when(bean).run();
        var stage = invoker.invoke(scheduledExecution);
        assertThat(stage).isCompleted();
        verify(bean).run();
    }

    @Test
    void givenMethod_whenThrowsException_thenCompleteStageExceptionally() {
        doThrow(RuntimeException.class).when(bean).run();
        var stage = invoker.invoke(scheduledExecution);
        assertThat(stage).isCompletedExceptionally();
        verify(bean).run();
    }

    interface TestBean {
        void run();
    }

}
