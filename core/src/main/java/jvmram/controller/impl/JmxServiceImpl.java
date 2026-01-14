package jvmram.controller.impl;

import jvmram.controller.JmxService;
import jvmram.jmx.JmxBeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class JmxServiceImpl implements JmxService {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private JmxServiceImpl() {
    }

    @Override
    public void gc(int pid) {
        var datum = JmxBeanFactory.getInstance().getMxDatum(pid);

        if (datum == null) {
            LOG.info("No JMX datum for process {}, the one might be already closed", pid);
            return;
        }

        try {
            datum.memory().gc();
        } catch (Exception e) {
            LOG.info("Failed to gc pid {}: {}", pid, e.getMessage());
        }
    }

    @Override
    public void createHeapDump(String filepath) {
        // todo: implement
    }

    public static final JmxServiceImpl INSTANCE = new JmxServiceImpl();
}
