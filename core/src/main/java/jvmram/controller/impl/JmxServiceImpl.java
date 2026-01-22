package jvmram.controller.impl;

import jvmram.controller.JmxService;
import jvmram.jmx.JmxBeanFactory;
import jvmram.jmx.MxDatum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static com.sun.management.HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN;

public class JmxServiceImpl implements JmxService {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private JmxServiceImpl() {
    }

    @Override
    public void gc(int pid) {
        var datum = getMxDatum(pid);
        if (datum == null) {
            return;
        }

        try {
            datum.memory().gc();
        } catch (Exception e) {
            LOG.info("Failed to gc pid {}: {}", pid, e.getMessage());
        }
    }

    @Override
    public void createHeapDump(int pid, String outputHprofFilePath) {
        var datum = getMxDatum(pid);
        if (datum == null) {
            return;
        }

        try {
            datum.hotSpot().dumpHeap(outputHprofFilePath, true);
        } catch (Exception e) {
            LOG.info("Failed to dump heap pid {}: {}", pid, e.getMessage());
        }
    }

    @Override
    public void createThreadDump(int pid, String outputThreadDumpPath) {
        var datum = getMxDatum(pid);
        if (datum == null) {
            return;
        }

        try {
            datum.hotSpot().dumpThreads(outputThreadDumpPath, TEXT_PLAIN);
        } catch (Exception e) {
            LOG.info("Failed to thread dump pid {}: {}", pid, e.getMessage());
        }
    }

    private static MxDatum getMxDatum(int pid) {
        var datum = JmxBeanFactory.getInstance().getMxDatum(pid);
        if (datum == null) {
            LOG.info("No JMX datum for process {}, the one might be already closed", pid);
            return null;
        }
        return datum;
    }

    public static final JmxServiceImpl INSTANCE = new JmxServiceImpl();
}
