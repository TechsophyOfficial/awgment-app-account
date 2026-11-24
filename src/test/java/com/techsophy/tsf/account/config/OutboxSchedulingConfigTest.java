package com.techsophy.tsf.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxSchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OutboxSchedulingConfig.class);

    @Test
    void shouldEnableSchedulingByDefaultAndWhenWorkerEnabledIsTrue() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OutboxSchedulingConfig.class);
            assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
            ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getThreadNamePrefix()).startsWith("awgment-outbox-sched-");
        });

        contextRunner
                .withPropertyValues("awgment.outbox.worker-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxSchedulingConfig.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                    assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
                });
    }

    @Test
    void shouldDisableSchedulingWhenWorkerEnabledIsFalse() {
        contextRunner
                .withPropertyValues("awgment.outbox.worker-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxSchedulingConfig.class);
                    assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }
}
