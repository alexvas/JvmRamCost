package jvmram.controller.impl;

import jakarta.inject.Inject;
import jvmram.controller.AppScheduler;
import jvmram.controller.GraphController;
import jvmram.controller.ProcessController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class AppSchedulerImpl implements AppScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Duration UPDATE_UI_DELAY = Duration.ofMillis(100);

    private static final Duration JVM_PROCESSES_LOOKUP_DELAY = Duration.ofSeconds(1);

    private final ScheduledExecutorService backgroundTasksExecutor = Executors.newSingleThreadScheduledExecutor(
            runnable -> new Thread(runnable, "app-backend")
    );

    private final GraphController graphController;
    private final ProcessController processController;

    @Inject
    private AppSchedulerImpl(GraphController graphController, ProcessController processController) {
        this.graphController = graphController;
        this.processController = processController;
    }

    @Override
    public void start() {
        scheduleAtRate(graphController::update, UPDATE_UI_DELAY);
        scheduleWithDelay(processController::refreshAvailableJvmProcesses, JVM_PROCESSES_LOOKUP_DELAY);
    }

    private void scheduleWithDelay(Runnable runnable, @SuppressWarnings("SameParameterValue") Duration delay) {
        Runnable wrapped = getWrapped(runnable);
        backgroundTasksExecutor.scheduleWithFixedDelay(wrapped, 0, delay.toMillis(), MILLISECONDS);
    }

    private void scheduleAtRate(Runnable runnable, @SuppressWarnings("SameParameterValue") Duration delay) {
        Runnable wrapped = getWrapped(runnable);
        backgroundTasksExecutor.scheduleAtFixedRate(wrapped, 0, delay.toMillis(), MILLISECONDS);
    }

    private static Runnable getWrapped(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                LOG.error("Failed to execute the task", t);
                throw t;
            }
        };
    }
}
