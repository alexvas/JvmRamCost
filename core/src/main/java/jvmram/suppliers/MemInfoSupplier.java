package jvmram.suppliers;

import jvmram.conf.Config;
import jvmram.suppliers.data.MemInfoData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

import static jvmram.model.metrics.Os.LINUX;

class MemInfoSupplier extends AbstractFileReaderSupplier<MemInfoData> {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String VM_RSS_PREFIX = "VmRSS:";

    MemInfoSupplier(int pid) {
        super(
                pid,
                Path.of("/proc", String.valueOf(pid), "status")
                );
        if (Config.os != LINUX) {
            LOG.error("The supplier is intended for use in Linux OS only");
        } else {
            setInitialized();
        }
    }

    private long rssInBytes = -1;

    @Override
    void startFileParse() {
        rssInBytes = -1;
    }

    @Override
    boolean parseLine(String line) {
        if (!line.startsWith(VM_RSS_PREFIX)) {
            return true;
        }

        var kb = secondItem(line, VM_RSS_PREFIX);
        rssInBytes = kilobytesToBytes(kb);
        return false;
    }

    @Override
    @Nullable
    MemInfoData parsedData() {
        return rssInBytes < 0
                ? null
                : new MemInfoData(rssInBytes);
    }
}
