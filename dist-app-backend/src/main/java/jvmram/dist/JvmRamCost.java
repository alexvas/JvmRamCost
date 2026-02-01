package jvmram.dist;

import jvmram.backend.JvmRunCostStarter;
import jvmram.di.CoreDiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.dimension.di.DimensionDI;
import ru.dimension.di.ServiceLocator;

import java.lang.invoke.MethodHandles;

public class JvmRamCost {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int DEFAULT_PORT = 53535;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((ignored, e) -> LOG.error("Unexpected exception: ", e));

        var builder = DimensionDI.builder();
        CoreDiConfig.config(builder);

        builder
                .scanPackages("jvmram")
                .buildAndInit();

        var main = ServiceLocator.get(JvmRunCostStarter.class);
        main.setup(DEFAULT_PORT);
        main.blockUntilShutdown();
    }
}
