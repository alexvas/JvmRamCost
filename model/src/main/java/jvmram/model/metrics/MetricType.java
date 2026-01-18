package jvmram.model.metrics;

import java.util.EnumSet;

import static jvmram.model.metrics.Os.LINUX;
import static jvmram.model.metrics.Os.WINDOWS;

public enum MetricType {
    RSS(EnumSet.of(LINUX), "Resident Set Size"),
    PSS(EnumSet.of(LINUX), "Proportional Set Size"),
    USS(EnumSet.of(LINUX), "Unique Set Size"),
    WS(EnumSet.of(WINDOWS), "Working Set"),
    PB(EnumSet.of(WINDOWS), "Private Bytes"),
    HEAP_USED(EnumSet.allOf(Os.class), "Heap Used"),
    HEAP_COMMITTED(EnumSet.allOf(Os.class), "Heap Committed"),
    NMT_USED(EnumSet.allOf(Os.class), "Native Memory Used"),
    NMT_COMMITTED(EnumSet.allOf(Os.class), "Native Memory Committed"),
    BUFFER_TOTAL(EnumSet.allOf(Os.class), "Buffer Total"),
    ;
    
    private final EnumSet<Os> applicable;
    private final String displayName;

    MetricType(EnumSet<Os> applicable, String displayName) {
        this.applicable = applicable;
        this.displayName = displayName;
    }

    public boolean isApplicable(Os input) {
        return applicable.contains(input);
    }

    public String getDisplayName() {
        return displayName;
    }
}
