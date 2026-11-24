package com.techsophy.tsf.account.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Enables Spring scheduling for the coalesced Keycloak outbox worker with a dedicated
 * graceful-shutdown {@link ThreadPoolTaskScheduler}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "awgment.outbox.worker-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedulingConfig implements SchedulingConfigurer {

    private static final int SCHEDULER_POOL_SIZE = 2;
    private static final int AWAIT_TERMINATION_SECONDS = 15;

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler outboxTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("awgment-outbox-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(outboxTaskScheduler());
    }
}
