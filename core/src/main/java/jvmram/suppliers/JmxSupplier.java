package jvmram.suppliers;

import jakarta.inject.Inject;
import jvmram.jmx.JmxBeanFactory;
import jvmram.jmx.MxDatum;
import jvmram.suppliers.data.JmxData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.dimension.di.Assisted;

import java.lang.invoke.MethodHandles;
import java.lang.management.BufferPoolMXBean;

public class JmxSupplier extends AbstractDataSupplier<JmxData> {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final MxDatum mxDatum;

    @Inject
    JmxSupplier(@Assisted int pid, JmxBeanFactory jmxBeanFactory) {
        super(pid);
        this.mxDatum = jmxBeanFactory.getMxDatum(pid);
        if (this.mxDatum != null) {
            setInitialized();
        } else {
            LOG.warn("No memory bean, failed to initialize for pid {}.", pid);
        }
    }
    
    @Override
    @Nullable JmxData doGetData() {
        if (mxDatum == null) {
            return null;
        }
        var memory = mxDatum.memory();
        if (memory == null) {
            return null;
        }

        long heapUsed = 0;
        long heapCommitted = 0;
        long nmtUsed = 0;
        long nmtCommitted = 0;
        long bufferTotal = 0;

        long oldGenMax = 0;
        long oldGenCommitted = 0;
        long oldGenUsed = 0;

        // Получаем информацию о heap памяти:
        // это Eden, Survivor, Old Gen.
        var heapMemoryUsage = memory.getHeapMemoryUsage();
        if (heapMemoryUsage != null) {
            heapUsed = heapMemoryUsage.getUsed();
            heapCommitted = heapMemoryUsage.getCommitted();
        }

        // И отдельно про старшее поколение
        var oldGen = mxDatum.oldGenPool();
        if (oldGen != null) {
            var oldGenUsage = oldGen.getUsage();
            if (oldGenUsage != null) {
                oldGenMax = oldGenUsage.getMax();
                oldGenCommitted = oldGenUsage.getCommitted();
                oldGenUsed = oldGenUsage.getUsed();
            }
        }
        
        // Получаем информацию о non-heap памяти (NMT)
        // это Metaspace, Code Cache, Compressed Class Space
        var nonHeapMemoryUsage = memory.getNonHeapMemoryUsage();
        if (nonHeapMemoryUsage != null) {
            nmtUsed = nonHeapMemoryUsage.getUsed();
            nmtCommitted = nonHeapMemoryUsage.getCommitted();
        }

        // Получаем информацию о Direct Memory:
        // Direct buffers / Mapped buffers/ etc.
        for (var pool : mxDatum.bufferPools()) {
            long used = pool.getMemoryUsed();
            bufferTotal += used < 0 // used == -1 if JVM is unable to give an estimate
                    ? pool.getTotalCapacity()
                    : used;
        }

        return new JmxData(
                heapUsed,
                heapCommitted,
                oldGenMax,
                oldGenCommitted,
                oldGenUsed,
                nmtUsed,
                nmtCommitted,
                mxDatum.bufferPools().size(),
                bufferTotal,
                mxDatum.agentProps(),
                mxDatum.sysProps()
        );
    }
}
