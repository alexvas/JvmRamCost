package jvmram.suppliers;

import jvmram.jmx.JmxBeanFactory;
import jvmram.suppliers.data.JmxData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

class JmxSupplier extends AbstractDataSupplier<JmxData> {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final MemoryMXBean memoryMxBean;
    
    JmxSupplier(int pid) {
        super(pid);
        this.memoryMxBean = JmxBeanFactory.getInstance().getMemoryMxBean(pid);
        if (this.memoryMxBean != null) {
            setInitialized();
        } else {
            LOG.warn("No memory bean, failed to initialize for pid {}.", pid);
        }
    }
    
    @Override
    @Nullable JmxData doGetData() {
        if (memoryMxBean == null) {
            return null;
        }
        
        long heapUsed = 0;
        long heapCommitted = 0;
        long nmtUsed = 0;
        long nmtCommitted = 0;

        // Получаем информацию о heap памяти
        MemoryUsage heapMemoryUsage = memoryMxBean.getHeapMemoryUsage();
        if (heapMemoryUsage != null) {
            heapUsed = heapMemoryUsage.getUsed();
            heapCommitted = heapMemoryUsage.getCommitted();
        }
        
        // Получаем информацию о non-heap памяти (NMT)
        MemoryUsage nonHeapMemoryUsage = memoryMxBean.getNonHeapMemoryUsage();
        if (nonHeapMemoryUsage != null) {
            nmtUsed = nonHeapMemoryUsage.getUsed();
            nmtCommitted = nonHeapMemoryUsage.getCommitted();
        }
        
        return new JmxData(heapUsed, heapCommitted, nmtUsed, nmtCommitted);
    }
}
