package jvmram.di;

import jvmram.controller.AppScheduler;
import jvmram.controller.GraphController;
import jvmram.controller.JmxService;
import jvmram.controller.ProcessController;
import jvmram.controller.impl.AppSchedulerImpl;
import jvmram.controller.impl.GraphControllerImpl;
import jvmram.controller.impl.JmxServiceImpl;
import jvmram.controller.impl.ProcessControllerImpl;
import jvmram.jmx.JmxBeanFactory;
import jvmram.jmx.impl.JmxBeanFactoryImpl;
import jvmram.metrics.MetricsFactory;
import jvmram.metrics.impl.MetricsFactoryImpl;
import jvmram.process.ProcessManager;
import jvmram.process.iml.ProcessManagerImpl;
import jvmram.suppliers.HardwareDataSuppliersFactory;
import jvmram.suppliers.HardwareDataSuppliersFactoryImpl;
import jvmram.suppliers.JmxSupplier;
import jvmram.suppliers.JmxSupplierFactory;
import jvmram.visibility.MetricVisibility;
import jvmram.visibility.impl.MetricVisibilityImpl;
import ru.dimension.di.DimensionDI.Builder;

public class CoreDiConfig {

    private CoreDiConfig() {
    }

    public static Builder config(Builder builder) {
        return builder
                .bind(MetricVisibility.class, MetricVisibilityImpl.class)
                .bind(ProcessManager.class, ProcessManagerImpl.class)
                .bind(HardwareDataSuppliersFactory.class, HardwareDataSuppliersFactoryImpl.class)
                .bind(MetricsFactory.class, MetricsFactoryImpl.class)

                .bindFactory(JmxSupplierFactory.class, JmxSupplier.class)

                .bind(AppScheduler.class, AppSchedulerImpl.class)
                .bind(GraphController.class, GraphControllerImpl.class)
                .bind(JmxService.class, JmxServiceImpl.class)
                .bind(ProcessController.class, ProcessControllerImpl.class)
                .bind(JmxBeanFactory.class, JmxBeanFactoryImpl.class)
                ;
    }
}
