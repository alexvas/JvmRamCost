package jvmram.suppliers.data;

import java.util.Properties;

public record JmxData(
        long heapUsed,
        long heapCommitted,
        long nmtUsed,
        long nmtCommitted,
        Properties properties,
        Properties sysProps
) implements HardwareData {
}
