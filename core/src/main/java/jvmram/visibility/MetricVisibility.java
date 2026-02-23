package jvmram.visibility;

import jvmram.model.ui.metrics.MetricType;


public interface MetricVisibility {

    boolean isVisible(MetricType type);

    void setInvisible(MetricType type);

    void setVisible(MetricType type);
}
