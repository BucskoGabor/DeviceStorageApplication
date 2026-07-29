package hu.tanszek.device.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * AsyncConfig — @Async metódusok executor konfiguráció MDC propagation-nel.
 *
 * <p>Az MDC (request_id, user_id, stb.) nem propagálódik automatikusan
 * a @Async metódusokon — a TaskDecorator lemásolja az MDC-t a háttér-thread-re,
 * hogy az async log-ok is tartalmazzák a request_id-t.
 *
 * <p>Az MDC.put/remove manuálisan kell az async metódus végén cleanup-olni,
 * de a Spring 6.x @AutoClose MDC-vel automatikusan cleanup-ol.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "applicationTaskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(mdcPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * TaskDecorator, amely az MDC-t átmásolja a háttér-thread-re @Async metódusok előtt.
     *
     * <p>Így az async log-ok (audit log write, email küldés) is tartalmazzák
     * a request_id-t, user_email-t, stb.
     */
    private TaskDecorator mdcPropagatingTaskDecorator() {
        return runnable -> {
            // Az aktuális MDC context-et elmentjük (request_id, user_id, stb.)
            Map<String, String> contextMap = MDC.getCopyOfContextMap();

            // Runnable-öt wrapper-elve: async metódus előtt MDC.put, után MDC.remove
            return () -> {
                Map<String, String> previousContext = MDC.getCopyOfContextMap();
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    if (previousContext != null) {
                        MDC.setContextMap(previousContext);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}