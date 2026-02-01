package jvmram.db.pid;

/*
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    boot_session_id INT NOT NULL REFERENCES jvm_ram_cost_boot_session(id) ON DELETE CASCADE,
    pid INT NOT NULL,
    process_name TEXT NOT NULL,
    comment TEXT,
    process_state VARCHAR(20) NOT NULL,
    process_start_time TIMESTAMP NOT NULL,
    process_home_directory TEXT NOT NULL,
    jvm_major_version INT NOT NULL,
    jvm_version TEXT NOT NULL,
    gc_type VARCHAR(30),
    container_id TEXT,
    max_direct_memory_kib BIGINT,
    metaspace_max_kib BIGINT,
    xmx_kib BIGINT NOT NULL,
    xms_kib BIGINT NOT NULL,
    CONSTRAINT chk_state CHECK (
        process_state IN ('running', 'stopped', 'zombie')
    )

 */

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
        String gcType,
        @Nullable String containerId,
        long maxDirectMemoryKib,
        long metaspaceMaxKib,
        long xmxKib,
        long xmsKib
) {
}
