package jvmram.model.data;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;

public record JvmProcessInfo(
        int pid,
        @Nullable String processName,
        Instant start,
        Path homeDirectory,
        int jvmMajorVersion,
        String jvmVersion,
        String gcType,
        @Nullable String containerId,
        long maxDirectMemoryKib,

        /**
         * Metaspace, Code Cache, Compressed Class Space
         */
        long nmtMaxKib,
        long xmxKib,
        long xmsKib
) {
}
