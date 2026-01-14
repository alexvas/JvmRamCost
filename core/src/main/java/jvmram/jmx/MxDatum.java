package jvmram.jmx;

import com.sun.management.HotSpotDiagnosticMXBean;

import javax.management.remote.JMXConnector;
import java.lang.management.MemoryMXBean;
import java.util.Properties;

public record MxDatum(
        JMXConnector jmxConnector,
        MemoryMXBean memory,
        HotSpotDiagnosticMXBean hotSpot,
        Properties agentProps,
        Properties sysProps
) {
}
