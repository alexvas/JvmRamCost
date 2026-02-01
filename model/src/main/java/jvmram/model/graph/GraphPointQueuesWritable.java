package jvmram.model.graph;

import jvmram.model.graph.impl.GraphPointQueuesImpl;
import jvmram.model.metrics.MetricType;

import javax.swing.*;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

public interface GraphPointQueuesWritable extends GraphPointQueues {

    /**
     * Добавить точку измерения потребления определённого типа памяти для определённого PID в свою очередь.
     *
     * @param pid        - к какому процессу относится измерение
     * @param metricType - тип памяти
     * @param graphPoint - числовое значение (время / количество потребляемых байт)
     */
    List<GraphPoint> add(int pid, MetricType metricType, GraphPoint graphPoint);

    void handleExceed(Collection<GraphPoint> exceeds);
}
