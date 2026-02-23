package jvmram.backend;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jvmram.backend.impl.JvmRamBackendImpl;
import jvmram.backend.impl.JvmRamBackendManager;
import jvmram.controller.AppScheduler;
import jvmram.controller.GraphController;
import jvmram.controller.JmxService;
import jvmram.controller.ProcessController;
import jvmram.model.ui.graph.GraphPointQueuesWritable;
import jvmram.visibility.MetricVisibility;

@Singleton
public class JvmRunCostStarter {
    private final JvmRamBackendManager backendManager;
    private final AppScheduler appScheduler;
    private final JvmRamBackendImpl backend;

    @Inject
    JvmRunCostStarter(
            ProcessController processController,
            GraphController graphController,
            GraphPointQueuesWritable graphPointQueues,
            JmxService jmxService,
            MetricVisibility metricVisibility,
            AppScheduler appScheduler
    ) {
        this.backend = new JvmRamBackendImpl(
                processController,
                graphController,
                graphPointQueues,
                jmxService,
                metricVisibility
        );

        this.appScheduler = appScheduler;
        backendManager = new JvmRamBackendManager();
    }


    public void setup(int port) {
        backendManager.start(port, backend);
        appScheduler.start();
    }

    public void blockUntilShutdown() {
        backendManager.blockUntilShutdown();
    }
}
