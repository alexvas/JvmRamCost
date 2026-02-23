package jvmram.metrics;

import jvmram.model.data.Os;
import jvmram.model.ui.metrics.MetricType;

import java.util.Map;

public interface MetricsFactory {
    Map<MetricType, RamMetric> getOrCreateMetrics(Integer pid, Os os);
}
