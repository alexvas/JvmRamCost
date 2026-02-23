package jvmram.suppliers;

import jvmram.conf.Config;
import jvmram.suppliers.data.SmapsData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

import static jvmram.model.data.Os.LINUX;

class SmapsSupplier extends AbstractFileReaderSupplier<SmapsData> {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    SmapsSupplier(int pid) {
        super(
                pid,
                Path.of("/proc", String.valueOf(pid), "smaps_rollup")
                );
        if (Config.os != LINUX) {
            LOG.error("The supplier is intended for use in Linux OS only");
        } else {
            setInitialized();
        }
    }

    private long pss = -1;
    private long uss = -1;

    @Override
    void startFileParse() {
        pss = -1;
        uss = -1;
    }

    private static final String PSS_PREFIX = "Pss:";
    private static final String PRIVATE_CLEAN_PREFIX = "Private_Clean:";
    private static final String PRIVATE_DIRTY_PREFIX = "Private_Dirty:";

    @Override
    boolean parseLine(String line) {
        if (line.startsWith(PSS_PREFIX)) {
            pss += parseToBytes(line, PSS_PREFIX);
        } else if (line.startsWith(PRIVATE_CLEAN_PREFIX)) {
            uss += parseToBytes(line, PRIVATE_CLEAN_PREFIX);
        } else if (line.startsWith(PRIVATE_DIRTY_PREFIX)) {
            uss += parseToBytes(line, PRIVATE_DIRTY_PREFIX);
        }
        return true;
    }

    private long parseToBytes(String input, String prefix) {
        return kilobytesToBytes(secondItem(input, prefix));
    }

    @Override
    @Nullable
    SmapsData parsedData() {
        return pss < 0 || uss < 0
                ? null
                : new SmapsData(pss + 1, uss + 1);
    }
}
