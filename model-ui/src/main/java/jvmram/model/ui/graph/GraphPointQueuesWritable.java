package jvmram.model.ui.graph;

import jvmram.model.ui.metrics.MetricType;

import java.util.Collection;
import java.util.List;

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
