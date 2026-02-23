package jvmram.metrics;

import jvmram.model.ui.graph.GraphPoint;

import java.time.Duration;

public interface RamMetric {

    /**
     * Получить значение метрики в байтах
     *
     * @return либо неотрицательное значение в байтах, либо специальные константы (выше).
     */
    GraphPoint getGraphPoint();

    /**
     * Обновить таймаут опроса.
     *
     * @param pollInterval - новый таймаут опроса.
     */
    void updatePollInterval(Duration pollInterval);
}
