package jvmram.suppliers.data;

import java.util.Properties;

public record JmxData(
        long heapUsed,
        long heapCommitted,
        long oldGenMax,
        long oldGenCommitted,
        long oldGenUsed,
        long nmtUsed,
        long nmtCommitted,
        int bufferCount,
        long bufferTotal,
        Properties properties,
        Properties sysProps
) implements HardwareData {
}
