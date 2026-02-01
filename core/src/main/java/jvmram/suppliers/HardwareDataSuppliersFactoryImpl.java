package jvmram.suppliers;

import jakarta.inject.Inject;
import jvmram.model.metrics.MetricType;
import jvmram.suppliers.data.HardwareData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HardwareDataSuppliersFactoryImpl implements HardwareDataSuppliersFactory {

    private final JmxSupplierFactory jmxSupplierFactory;

    @Inject
    HardwareDataSuppliersFactoryImpl(JmxSupplierFactory jmxSupplierFactory) {
        this.jmxSupplierFactory = jmxSupplierFactory;
    }

    private final Map<Integer, Map<Class<? extends AbstractDataSupplier<?>>, AbstractDataSupplier<?>>> suppliers = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <T extends HardwareData> HardwareDataSupplier<T> getOrCreateSupplier(int pid, MetricType metricType) {
        return (AbstractDataSupplier<T>) suppliers.computeIfAbsent(
                pid,
                ignored -> new HashMap<>()
        ).computeIfAbsent(
                supplierClass(metricType),
                ignored2 -> doCreateSupplier(pid, metricType)
        );
    }

    private Class<? extends AbstractDataSupplier<?>> supplierClass(MetricType type) {
        return switch (type) {
            case RSS -> MemInfoSupplier.class;
            case PSS, USS -> SmapsSupplier.class;
            case WS, PB -> WinSupplier.class;
            case HEAP_COMMITTED, HEAP_USED, OLD_GEN_MAX, OLD_GEN_COMMITTED, OLD_GEN_USED, NMT_USED, NMT_COMMITTED,
                 BUFFER_TOTAL -> JmxSupplier.class;
        };
    }

    private AbstractDataSupplier<?> doCreateSupplier(int pid, MetricType type) {
        return switch (type) {
            case RSS -> new MemInfoSupplier(pid);
            case PSS, USS -> new SmapsSupplier(pid);
            case WS, PB -> new WinSupplier(pid);
            case HEAP_COMMITTED, HEAP_USED, OLD_GEN_MAX, OLD_GEN_COMMITTED, OLD_GEN_USED, NMT_USED, NMT_COMMITTED,
                 BUFFER_TOTAL -> jmxSupplierFactory.create(pid);
        };
    }
}
