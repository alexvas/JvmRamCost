package jvmram.metrics;

import jvmram.metrics.impl.MetricsFactoryImpl;
import jvmram.model.metrics.MetricType;
import jvmram.model.metrics.Os;

import java.util.Map;

public interface MetricsFactory {
    Map<MetricType, RamMetric> getOrCreateMetrics(Integer pid, Os os);

    static MetricsFactory getInstance() {
        return MetricsFactoryImpl.INSTANCE;
    }
}
