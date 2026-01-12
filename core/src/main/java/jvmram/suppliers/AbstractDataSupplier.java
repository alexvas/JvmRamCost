package jvmram.suppliers;

import jvmram.suppliers.data.HardwareData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;

abstract class AbstractDataSupplier<T extends HardwareData> implements HardwareDataSupplier<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    final int pid;

    private boolean initialized;
    private Instant lastPollInstant;
    private T stored;

    AbstractDataSupplier(int pid) {
        this.pid = pid;
    }

    @Override
    public void measureAndStore() {
        if (!initialized) {
            return;
        }

        lastPollInstant = Instant.now();

        try {
            stored = doGetData();
        } catch (Exception e) {
            LOG.error("Error getting data for pid {}", pid);
            stored = null;
        }
    }

    @Override
    public @Nullable T getStoredData() {
        if (!initialized) {
            return null;
        }
        return stored;
    }

        @Override
    public @Nullable Instant lastPollInstant() {
        return lastPollInstant;
    }

    /**
     * Метод для использования (и переопределения) в потомках,
     * который помечает Поставщик как рабочий.
     */
    void setInitialized() {
        this.initialized = true;
    }

    abstract T doGetData();
}
