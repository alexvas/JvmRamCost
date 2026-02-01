package jvmram.metrics.impl;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jvmram.metrics.MetricsFactory;
import jvmram.metrics.RamMetric;
import jvmram.model.metrics.MetricType;
import jvmram.model.metrics.Os;
import jvmram.suppliers.HardwareDataSupplier;
import jvmram.suppliers.HardwareDataSuppliersFactory;
import jvmram.suppliers.data.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static jvmram.conf.Config.DEV_POLL_INTERVALS;
import static jvmram.model.metrics.MetricType.*;

@Singleton
public class MetricsFactoryImpl implements MetricsFactory {

    private final Map<Integer, Map<MetricType, RamMetric>> metrics = new ConcurrentHashMap<>();

    private final HardwareDataSuppliersFactory suppliersFactory;

    @Inject
    MetricsFactoryImpl(HardwareDataSuppliersFactory suppliersFactory) {
        this.suppliersFactory = suppliersFactory;
    }

    @Override
    public Map<MetricType, RamMetric> getOrCreateMetrics(Integer pid, Os os) {
        return metrics.computeIfAbsent(pid, ignored -> createMetricMap(pid, os));
    }

    private Map<MetricType, RamMetric> createMetricMap(int pid, Os os) {
        var osSpecific = switch (os) {
            case LINUX -> Map.of(
                    RSS, createMetrics(pid, RSS, MemInfoData::rss),
                    PSS, createMetrics(pid, PSS, SmapsData::pss),
                    USS, createMetrics(pid, USS, SmapsData::uss)
            );
            case WINDOWS -> Map.of(
                    PB, createMetrics(pid, PB, WinData::pb),
                    WS, createMetrics(pid, WS, WinData::ws)
            );
        };
        var common = Map.of(
                HEAP_USED, createMetrics(pid, HEAP_USED, JmxData::heapUsed),
                HEAP_COMMITTED, createMetrics(pid, HEAP_COMMITTED, JmxData::heapCommitted),
                OLD_GEN_MAX, createMetrics(pid, OLD_GEN_MAX, JmxData::oldGenMax),
                OLD_GEN_COMMITTED, createMetrics(pid, OLD_GEN_COMMITTED, JmxData::oldGenCommitted),
                OLD_GEN_USED, createMetrics(pid, OLD_GEN_USED, JmxData::oldGenUsed),
                NMT_USED, createMetrics(pid, NMT_USED, JmxData::nmtUsed),
                NMT_COMMITTED, createMetrics(pid, NMT_COMMITTED, JmxData::nmtCommitted),
                BUFFER_TOTAL, createMetrics(pid, BUFFER_TOTAL, JmxData::bufferTotal)
        );
        var output = new HashMap<>(osSpecific);
        output.putAll(common);
        return output;
    }

    private <T extends HardwareData> RamMetric createMetrics(int pid, MetricType type, Function<T, Long> converter) {
        HardwareDataSupplier<T> supplier = suppliersFactory.getOrCreateSupplier(pid, type);
        return new BaseMetric<>(supplier, DEV_POLL_INTERVALS.get(type), converter);
    }
}
