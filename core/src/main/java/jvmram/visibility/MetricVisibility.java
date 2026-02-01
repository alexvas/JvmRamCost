package jvmram.visibility;

import jvmram.visibility.impl.MetricVisibilityImpl;
import jvmram.model.metrics.MetricType;


public interface MetricVisibility {

    boolean isVisible(MetricType type);

    void setInvisible(MetricType type);

    void setVisible(MetricType type);
}
