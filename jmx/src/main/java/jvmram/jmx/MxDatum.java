package jvmram.jmx;

import com.sun.management.HotSpotDiagnosticMXBean;

import javax.management.remote.JMXConnector;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;
import java.util.Properties;

public record MxDatum(
        JMXConnector jmxConnector,
        MemoryMXBean memory,
        MemoryPoolMXBean oldGenPool,
        HotSpotDiagnosticMXBean hotSpot,
        List<BufferPoolMXBean> bufferPools,
        Properties agentProps,
        Properties sysProps
) {
}
