package jvmram.di;

import jvmram.suppliers.JmxSupplier;
import jvmram.suppliers.JmxSupplierFactory;
import ru.dimension.di.DimensionDI.Builder;

public class CoreDiConfig {

    private CoreDiConfig() {
    }

    public static Builder config(Builder builder) {
        return builder
                .bindFactory(JmxSupplierFactory.class, JmxSupplier.class)
                ;
    }
}
