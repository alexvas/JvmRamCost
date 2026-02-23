package jvmram.model.data;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;

public record ProcessInfoInput(
        int pid,
        String processName,
        Instant start,
        Path homeDirectory,
        int jvmMajorVersion,
        String jvmVersion,
        String gcType,
        @Nullable String containerId,
        long maxDirectMemoryKib,
        long metaspaceMaxKib,
        long xmxKib,
        long xmsKib
) {
}
