package jvmram.db.pid;

import jvmram.db.boot.BootSessionInfo;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;

public record ProcessInfo(
        long id,
        BootSessionInfo bootSessionInfo,
        int pid,
        String processName,
        String comment,
        ProcessState state,
        Instant start,
        Path homeDirectory,
        int jvmMajorVersion,
        String jvmVersion,
        @Nullable String gcType,
        @Nullable String containerId,
        long maxDirectMemoryKib,
        long nmtMaxKib,
        long xmxKib,
        long xmsKib
) {
}
