package jvmram.visibility.impl;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jvmram.visibility.MetricVisibility;
import jvmram.model.ui.metrics.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Singleton
public class MetricVisibilityImpl implements MetricVisibility {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Set<MetricType> invisibles = Collections.synchronizedSet(EnumSet.noneOf(MetricType.class));

    @Inject
    MetricVisibilityImpl() {
    }

    @Override
    public boolean isVisible(MetricType type) {
        return !invisibles.contains(type);
    }

    @Override
    public void setInvisible(MetricType type) {
        LOG.trace("before setting invisible: {}", invisibles);
        invisibles.add(type);
        LOG.trace("after setting invisible: {}", invisibles);
    }

    @Override
    public void setVisible(MetricType type) {
        LOG.trace("before setting visible: {}", invisibles);
        invisibles.remove(type);
        LOG.trace("after setting visible: {}", invisibles);
    }
}
