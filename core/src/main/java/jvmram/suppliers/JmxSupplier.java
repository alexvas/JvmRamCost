package jvmram.suppliers;

import jvmram.jmx.JmxBeanFactory;
import jvmram.jmx.MxDatum;
import jvmram.suppliers.data.JmxData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

class JmxSupplier extends AbstractDataSupplier<JmxData> {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final MxDatum mxDatum;
    
    JmxSupplier(int pid) {
        super(pid);
        this.mxDatum = JmxBeanFactory.getInstance().getMxDatum(pid);
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

        // Получаем информацию о heap памяти
        var heapMemoryUsage = memory.getHeapMemoryUsage();
        if (heapMemoryUsage != null) {
            heapUsed = heapMemoryUsage.getUsed();
            heapCommitted = heapMemoryUsage.getCommitted();
        }
        
        // Получаем информацию о non-heap памяти (NMT)
        var nonHeapMemoryUsage = memory.getNonHeapMemoryUsage();
        if (nonHeapMemoryUsage != null) {
            nmtUsed = nonHeapMemoryUsage.getUsed();
            nmtCommitted = nonHeapMemoryUsage.getCommitted();
        }

        return new JmxData(
                heapUsed,
                heapCommitted,
                nmtUsed,
                nmtCommitted,
                mxDatum.agentProps(),
                mxDatum.sysProps()
        );
    }
}
