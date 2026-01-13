package jvmram.dist;

import jvmram.backend.JvmRunCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class JvmRamCost {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int DEFAULT_PORT = 53535;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((ignored, e) -> LOG.error("Unexpected exception: ", e));

        var main = new JvmRunCost();
        main.setup(DEFAULT_PORT);
        main.blockUntilShutdown();
    }
}
