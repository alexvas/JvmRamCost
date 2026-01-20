package jvmram.model.metrics;

import java.util.EnumSet;

import static jvmram.model.metrics.Os.LINUX;
import static jvmram.model.metrics.Os.WINDOWS;

public enum MetricType {
    RSS(EnumSet.of(LINUX)),
    PSS(EnumSet.of(LINUX)),
    USS(EnumSet.of(LINUX)),
    WS(EnumSet.of(WINDOWS)),
    PB(EnumSet.of(WINDOWS)),
    HEAP_USED(EnumSet.allOf(Os.class)),
    HEAP_COMMITTED(EnumSet.allOf(Os.class)),
    OLD_GEN_MAX(EnumSet.allOf(Os.class)),
    OLD_GEN_COMMITTED(EnumSet.allOf(Os.class)),
    OLD_GEN_USED(EnumSet.allOf(Os.class)),
    NMT_USED(EnumSet.allOf(Os.class)),
    NMT_COMMITTED(EnumSet.allOf(Os.class)),
    BUFFER_TOTAL(EnumSet.allOf(Os.class)),
    ;
    
    private final EnumSet<Os> applicable;

    MetricType(EnumSet<Os> applicable) {
        this.applicable = applicable;
    }

    public boolean isApplicable(Os input) {
        return applicable.contains(input);
    }
}
