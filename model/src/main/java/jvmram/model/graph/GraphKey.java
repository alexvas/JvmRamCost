package jvmram.model.graph;

import jvmram.model.metrics.MetricType;

public record GraphKey(MetricType type, int pid) {
}
