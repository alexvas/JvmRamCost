package jvmram.model.ui.graph;

import jvmram.model.ui.metrics.MetricType;

public record GraphKey(MetricType type, int pid) {
}
